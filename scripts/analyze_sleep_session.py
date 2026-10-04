#!/usr/bin/env python3
"""
DozeDial Phase 2 Feasibility Analysis Script
Analyzes overnight heart rate CSV telemetry recorded from physical Fitbit Air / BLE devices.

Uses only Python standard library (no external dependencies required).
"""

import argparse
import csv
import datetime
import math
import statistics
import sys
from typing import List, Dict, Any, Optional

def parse_csv(file_path: str) -> List[Dict[str, Any]]:
    records = []
    with open(file_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                bpm = int(row["bpm"])
                if bpm <= 0:
                    continue  # Filter zero readings
                records.append({
                    "session_id": row.get("session_id"),
                    "iso_timestamp": row.get("iso_timestamp"),
                    "timestamp_epoch_ms": int(row["timestamp_epoch_ms"]),
                    "elapsed_seconds": int(row["elapsed_seconds"]),
                    "bpm": bpm,
                    "sensor_contact": row.get("sensor_contact") == "true",
                    "raw_hex": row.get("raw_hex", "")
                })
            except (ValueError, KeyError):
                continue
    return records

def print_ascii_chart(records: List[Dict[str, Any]], width: int = 70, height: int = 14):
    if not records:
        return

    # Downsample to `width` bins
    chunk_size = max(1, len(records) // width)
    sampled_bpm = []
    for i in range(0, len(records), chunk_size):
        chunk = records[i:i + chunk_size]
        avg = sum(r["bpm"] for r in chunk) / len(chunk)
        sampled_bpm.append(avg)
    sampled_bpm = sampled_bpm[:width]

    min_bpm = math.floor(min(sampled_bpm))
    max_bpm = math.ceil(max(sampled_bpm))
    bpm_range = max(1, max_bpm - min_bpm)

    print("\n" + "=" * width)
    print("   OVERNIGHT HEART RATE PROFILE (BPM OVER TIME)")
    print("=" * width)

    for h in range(height, -1, -1):
        target_bpm = min_bpm + (h / height) * bpm_range
        label = f"{int(round(target_bpm)):3d} | "
        line = []
        for val in sampled_bpm:
            val_h = ((val - min_bpm) / bpm_range) * height
            if abs(val_h - h) < 0.5:
                line.append("•")
            elif val_h > h:
                line.append(" ")
            else:
                line.append(" ")
        print(label + "".join(line))

    # X axis
    print("    +" + "-" * len(sampled_bpm))
    start_time = datetime.datetime.fromtimestamp(records[0]["timestamp_epoch_ms"] / 1000.0)
    end_time = datetime.datetime.fromtimestamp(records[-1]["timestamp_epoch_ms"] / 1000.0)
    start_str = start_time.strftime("%H:%M")
    end_str = end_time.strftime("%H:%M")
    spacing = " " * (len(sampled_bpm) - len(start_str) - len(end_str))
    print(f"     {start_str}{spacing}{end_str}")

def analyze_session(records: List[Dict[str, Any]]):
    if not records:
        print("[!] No valid heart rate records found in file.")
        return

    total_samples = len(records)
    start_time = datetime.datetime.fromtimestamp(records[0]["timestamp_epoch_ms"] / 1000.0)
    end_time = datetime.datetime.fromtimestamp(records[-1]["timestamp_epoch_ms"] / 1000.0)
    duration_sec = records[-1]["elapsed_seconds"] - records[0]["elapsed_seconds"]
    hours = duration_sec // 3600
    minutes = (duration_sec % 3600) // 60
    seconds = duration_sec % 60

    bpms = [r["bpm"] for r in records]
    avg_bpm = statistics.mean(bpms)
    min_bpm = min(bpms)
    max_bpm = max(bpms)
    median_bpm = statistics.median(bpms)
    stdev_bpm = statistics.stdev(bpms) if len(bpms) > 1 else 0.0

    # 1. Packet drop / sampling integrity check
    intervals = []
    for i in range(1, len(records)):
        diff = (records[i]["timestamp_epoch_ms"] - records[i-1]["timestamp_epoch_ms"]) / 1000.0
        intervals.append(diff)
    
    avg_interval = statistics.mean(intervals) if intervals else 1.0
    large_gaps = [g for g in intervals if g > 3.0]

    # 2. Resting Heart Rate (RHR) estimation (lowest 10th percentile during sleep)
    sorted_bpms = sorted(bpms)
    rhr = sorted_bpms[int(len(sorted_bpms) * 0.10)]

    # 3. 5-Minute Epoch Sliding Window Analysis
    epoch_duration_sec = 300  # 5 minutes
    epochs = []
    current_epoch_records = []
    epoch_start_time = records[0]["elapsed_seconds"]

    for r in records:
        if r["elapsed_seconds"] - epoch_start_time < epoch_duration_sec:
            current_epoch_records.append(r["bpm"])
        else:
            if len(current_epoch_records) >= 30:
                e_mean = statistics.mean(current_epoch_records)
                e_std = statistics.stdev(current_epoch_records) if len(current_epoch_records) > 1 else 0
                epochs.append({"mean": e_mean, "std": e_std, "samples": len(current_epoch_records)})
            epoch_start_time = r["elapsed_seconds"]
            current_epoch_records = [r["bpm"]]

    # Criteria from Phase 2 Feasibility Study:
    # Deep sleep: ultra-stable low heart rate, std < 1.5 BPM
    # Wake spikes: abrupt jumps
    deep_sleep_epochs = [e for e in epochs if e["std"] < 1.5 and e["mean"] < (avg_bpm + 2)]
    active_epochs = [e for e in epochs if e["std"] >= 3.0 or e["mean"] > (avg_bpm + 5)]
    light_rem_epochs = [e for e in epochs if e not in deep_sleep_epochs and e not in active_epochs]

    deep_sleep_pct = (len(deep_sleep_epochs) / len(epochs) * 100) if epochs else 0
    light_rem_pct = (len(light_rem_epochs) / len(epochs) * 100) if epochs else 0
    active_wake_pct = (len(active_epochs) / len(epochs) * 100) if epochs else 0

    # Print Report
    print("\n" + "=" * 70)
    print("           DOZEDIAL SLEEP SIGNAL FEASIBILITY REPORT")
    print("=" * 70)
    print(f" Session ID         : {records[0]['session_id']}")
    print(f" Start Time         : {start_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f" End Time           : {end_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f" Total Duration     : {hours}h {minutes:02d}m {seconds:02d}s ({duration_sec}s)")
    print(f" Packets Received   : {total_samples} samples")
    print(f" Expected 1Hz Count : {duration_sec} samples")
    packet_retention = (total_samples / max(1, duration_sec)) * 100
    print(f" Packet Capture Rate: {packet_retention:.1f}%")
    print(f" Gaps (> 3s drop)   : {len(large_gaps)} occurrences")
    print("-" * 70)
    print("   HEART RATE TELEMETRY DYNAMICS")
    print(f" Minimum BPM        : {min_bpm}")
    print(f" Maximum BPM        : {max_bpm}")
    print(f" Average BPM        : {avg_bpm:.1f}")
    print(f" Median BPM         : {median_bpm:.1f}")
    print(f" Overall Std Dev    : {stdev_bpm:.2f} BPM")
    print(f" Estimated RHR      : {rhr} BPM (10th percentile)")
    print("-" * 70)
    print(f"   5-MINUTE EPOCH STAGE ESTIMATOR ({len(epochs)} epochs evaluated)")
    print(f" Deep Sleep Candidate  (StdDev < 1.5 BPM) : {len(deep_sleep_epochs):2d} epochs ({deep_sleep_pct:.1f}%)")
    print(f" Light / REM Candidate (StdDev 1.5–3.0)   : {len(light_rem_epochs):2d} epochs ({light_rem_pct:.1f}%)")
    print(f" Wake / Arousal Spikes (StdDev >= 3.0)    : {len(active_epochs):2d} epochs ({active_wake_pct:.1f}%)")
    print("=" * 70)

    # Feasibility Verdict
    print("\n>>> FEASIBILITY VERDICT <<<")
    if duration_sec < 1800:
        print(" [i] Short session (< 30 minutes). Data pipeline is functional.")
        print("     To perform full overnight sleep stage classification, record an 8+ hour sleep.")
    else:
        if len(deep_sleep_epochs) > 0 and len(active_epochs) > 0:
            print(" [PASS] Clear physiological divergence detected between stable deep sleep epochs")
            print("        and high-variance wake/arousal windows. High-resolution BPM without RR intervals")
            print("        provides a strong empirical basis for Phase 3's smart-alarm estimator.")
        else:
            print(" [NOTE] Session recorded. Inspect ASCII profile below for diurnal dips and wake spikes.")

    print_ascii_chart(records)

def main():
    parser = argparse.ArgumentParser(description="Analyze DozeDial overnight heart rate CSV recording")
    parser.add_argument("csv_file", help="Path to exported CSV file from DozeDial")
    args = parser.parse_args()

    records = parse_csv(args.csv_file)
    analyze_session(records)

if __name__ == "__main__":
    main()
