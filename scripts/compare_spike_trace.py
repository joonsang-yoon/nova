#!/usr/bin/env python3
import argparse
import json
import re
import sys

U32 = 0xFFFFFFFF
COMMIT_RE = re.compile(
    r"core\s+\d+:\s+\d+\s+(0x[0-9a-fA-F]+)\s+\((0x[0-9a-fA-F]+)\)(.*)$"
)
REG_RE = re.compile(r"\bx([0-9]+)\s+(0x[0-9a-fA-F]+)")
MEM_RE = re.compile(r"\bmem\s+(0x[0-9a-fA-F]+)(?:\s+(0x[0-9a-fA-F]+))?")


def parse_int(text):
    if text is None or text == "":
        return None
    return int(text, 0) & U32


def load_nova(path):
    out = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            item = json.loads(line)
            if item.get("retired") and not item.get("trap"):
                out.append(item)
    return out


def load_spike(path, start, tohost, fromhost):
    commits = []
    xregs = [0] * 32
    started = False
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            m = COMMIT_RE.search(line)
            if not m:
                continue
            pc = int(m.group(1), 16) & U32
            if not started:
                if pc != start:
                    continue
                started = True
            instr = int(m.group(2), 16) & U32
            rest = m.group(3)
            rd_match = REG_RE.search(rest)
            rd = None
            rd_value = None
            if rd_match:
                rd = int(rd_match.group(1))
                rd_value = int(rd_match.group(2), 16) & U32
                if rd != 0:
                    xregs[rd] = rd_value
                    xregs[0] = 0
            mem_match = MEM_RE.search(rest)
            mem_addr = None
            mem_value = None
            if mem_match:
                mem_addr = int(mem_match.group(1), 16) & U32
                mem_value = parse_int(mem_match.group(2))
            commits.append(
                {
                    "pc": pc,
                    "instr": instr,
                    "rd": rd,
                    "rdValue": rd_value,
                    "memAddr": mem_addr,
                    "memValue": mem_value,
                    "xregs": list(xregs),
                    "line": line.strip(),
                }
            )
            if mem_addr in (tohost, fromhost):
                break
    return commits


def mmio_masked(addr, tohost, fromhost):
    if addr is None:
        return False
    if addr in (tohost, fromhost, (tohost + 4) & U32, (fromhost + 4) & U32):
        return True
    return 0x02000000 <= addr <= 0x0200FFFF


def compare(nova, spike, start, tohost, fromhost):
    failures = []
    masks = []
    count = min(len(nova), len(spike))
    if len(nova) != len(spike):
      failures.append(f"retire count mismatch: nova={len(nova)} spike={len(spike)}")

    for idx in range(count):
        n = nova[idx]
        s = spike[idx]
        pc = parse_int(n["pc"])
        instr = parse_int(n["instr"])
        rd = int(n["rd"])
        rd_wen = bool(n["rdWen"])
        rd_value = parse_int(n["rdValue"])
        if pc != s["pc"]:
            failures.append(f"{idx}: pc mismatch nova=0x{pc:08x} spike=0x{s['pc']:08x}")
            break
        if instr != s["instr"]:
            failures.append(f"{idx}: instr mismatch pc=0x{pc:08x} nova=0x{instr:08x} spike=0x{s['instr']:08x}")
            break
        if rd_wen:
            if s["rd"] != rd:
                failures.append(f"{idx}: rd mismatch pc=0x{pc:08x} nova=x{rd} spike=x{s['rd']}")
                break
            if rd_value != s["rdValue"]:
                failures.append(
                    f"{idx}: rd value mismatch pc=0x{pc:08x} x{rd} nova=0x{rd_value:08x} spike=0x{s['rdValue']:08x}"
                )
                break
        elif s["rd"] not in (None, 0):
            failures.append(f"{idx}: unexpected spike rd write pc=0x{pc:08x} spike=x{s['rd']}")
            break

        nova_regs = [parse_int(v) for v in n["xregs"]]
        if nova_regs != s["xregs"]:
            for reg_idx, (nv, sv) in enumerate(zip(nova_regs, s["xregs"])):
                if nv != sv:
                    failures.append(
                        f"{idx}: xreg mismatch pc=0x{pc:08x} x{reg_idx} nova=0x{nv:08x} spike=0x{sv:08x}"
                    )
                    break
            break

        if bool(n["memValid"]):
            n_addr = parse_int(n["memAddr"])
            if mmio_masked(n_addr, tohost, fromhost):
                masks.append(f"{idx}: masked MMIO/HTIF memory access at 0x{n_addr:08x}")
            elif int(n["memAtomic"]) == 2 and parse_int(n["rdValue"]) == 1:
                masks.append(f"{idx}: masked failed SC.W non-store at 0x{n_addr:08x}")
            elif bool(n["memWrite"]):
                if s["memAddr"] != n_addr:
                    failures.append(
                        f"{idx}: store addr mismatch pc=0x{pc:08x} nova=0x{n_addr:08x} spike={s['memAddr']}"
                    )
                    break
                n_value = parse_int(n["memWdata"])
                size = int(n["memSize"])
                if size == 0:
                    n_value = (n_value >> (8 * (n_addr & 0x3))) & 0xFF
                elif size == 1:
                    n_value = (n_value >> (8 * (n_addr & 0x2))) & 0xFFFF
                if s["memValue"] is not None and s["memValue"] != n_value:
                    failures.append(
                        f"{idx}: store value mismatch pc=0x{pc:08x} nova=0x{n_value:08x} spike=0x{s['memValue']:08x}"
                    )
                    break

    status = "pass" if not failures and not masks else "masked-pass" if not failures else "fail"
    return {
        "status": status,
        "comparedRetires": count,
        "novaRetires": len(nova),
        "spikeRetires": len(spike),
        "start": f"0x{start:08x}",
        "tohost": f"0x{tohost:08x}",
        "fromhost": f"0x{fromhost:08x}",
        "masks": masks,
        "failures": failures,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--nova-trace", required=True)
    parser.add_argument("--spike-log", required=True)
    parser.add_argument("--start", required=True)
    parser.add_argument("--tohost", required=True)
    parser.add_argument("--fromhost", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    start = int(args.start, 0) & U32
    tohost = int(args.tohost, 0) & U32
    fromhost = int(args.fromhost, 0) & U32
    nova = load_nova(args.nova_trace)
    spike = load_spike(args.spike_log, start, tohost, fromhost)
    result = compare(nova, spike, start, tohost, fromhost)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2, sort_keys=True)
        f.write("\n")
    if result["status"] == "fail":
        print(json.dumps(result, indent=2, sort_keys=True), file=sys.stderr)
        return 1
    print(f"PASS: compared {result['comparedRetires']} retired instructions ({result['status']})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
