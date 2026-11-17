using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using LibreHardwareMonitor.Hardware;

namespace IanLabMonitor;

internal sealed class TelemetrySnapshot
{
    public DateTime At { get; set; } = DateTime.Now;
    public float CpuLoad { get; set; }
    public float? CpuTemp { get; set; }
    public float? GpuTemp { get; set; }
    public float? CpuPower { get; set; }
    public float? GpuPower { get; set; }
    public float? FanRpm { get; set; }
    public float? BatteryVoltage { get; set; }
    public float? BatteryPower { get; set; }
    public ulong TotalMemory { get; set; }
    public ulong UsedMemory { get; set; }
    public bool OnAc { get; set; }
    public int BatteryPercent { get; set; } = -1;
    public string Wifi { get; set; } = "N/A";
    public double NetDownMbps { get; set; }
    public string Error { get; set; } = "";
    public float RamPercent => TotalMemory == 0 ? 0 : 100f * UsedMemory / TotalMemory;
}

/// <summary>
/// Sampling is performed off the UI thread every 2 seconds. No drivers, EC writes or fan controls.
/// LibreHardwareMonitor sensors are read-only; missing sensors stay unavailable rather than fabricated.
/// </summary>
internal sealed class Telemetry : IDisposable
{
    private Computer? _hardware;
    private bool _hardwareUnavailable;
    private ulong _previousIdle, _previousKernel, _previousUser;
    private long _lastNetBytes;
    private DateTime _lastNetworkTick;

    public Telemetry()
    {
        try
        {
            _hardware = new Computer
            {
                IsCpuEnabled = true,
                IsGpuEnabled = true,
                IsBatteryEnabled = true,
                IsMotherboardEnabled = false,
                IsControllerEnabled = false,
                IsMemoryEnabled = false,
                IsNetworkEnabled = false,
                IsStorageEnabled = false
            };
            _hardware.Open();
        }
        catch (Exception)
        {
            _hardwareUnavailable = true;
            try { _hardware?.Close(); } catch { }
            _hardware = null;
        }
    }

    public TelemetrySnapshot Read()
    {
        TelemetrySnapshot s = new();
        try { ReadCpuAndRam(s); } catch { }
        try { ReadPower(s); } catch { }
        try { ReadWifi(s); } catch { }
        try { ReadSensors(s); } catch { s.Error = "Một số cảm biến không phản hồi"; }
        if (_hardwareUnavailable && s.CpuTemp is null && s.GpuTemp is null)
            s.Error = "Không truy cập được cảm biến; thử chạy bằng quyền Administrator";
        return s;
    }

    private void ReadCpuAndRam(TelemetrySnapshot s)
    {
        if (GetSystemTimes(out FileTime idle, out FileTime kernel, out FileTime user))
        {
            ulong i = idle.Value, k = kernel.Value, u = user.Value;
            if (_previousKernel != 0)
            {
                ulong total = k - _previousKernel + u - _previousUser;
                ulong idleDiff = i - _previousIdle;
                if (total > 0) s.CpuLoad = Math.Clamp(100f * (total - Math.Min(total, idleDiff)) / total, 0, 100);
            }
            _previousIdle = i; _previousKernel = k; _previousUser = u;
        }
        MemoryStatus m = new() { Length = (uint)Marshal.SizeOf<MemoryStatus>() };
        if (GlobalMemoryStatusEx(ref m))
        {
            s.TotalMemory = m.TotalPhysical;
            s.UsedMemory = m.TotalPhysical - m.AvailablePhysical;
        }
    }

    private static void ReadPower(TelemetrySnapshot s)
    {
        if (GetSystemPowerStatus(out SystemPowerStatus p))
        {
            s.OnAc = p.AcLineStatus == 1;
            s.BatteryPercent = p.BatteryLifePercent == 255 ? -1 : p.BatteryLifePercent;
        }
    }

    private void ReadWifi(TelemetrySnapshot s)
    {
        NetworkInterface? wifi = NetworkInterface.GetAllNetworkInterfaces().FirstOrDefault(n =>
            n.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 &&
            n.OperationalStatus == OperationalStatus.Up);
        if (wifi is null) { s.Wifi = "Mất kết nối"; _lastNetBytes = 0; return; }
        s.Wifi = wifi.Name;
        var st = wifi.GetIPv4Statistics();
        long bytes = st.BytesReceived;
        if (_lastNetworkTick != default && bytes >= _lastNetBytes)
        {
            double seconds = Math.Max(.1, (DateTime.UtcNow - _lastNetworkTick).TotalSeconds);
            s.NetDownMbps = (bytes - _lastNetBytes) * 8d / seconds / 1_000_000d;
        }
        _lastNetBytes = bytes;
        _lastNetworkTick = DateTime.UtcNow;
    }

    private void ReadSensors(TelemetrySnapshot s)
    {
        if (_hardware is null) return;
        foreach (IHardware h in _hardware.Hardware)
        {
            try { h.Update(); } catch { continue; }
            foreach (IHardware sub in h.SubHardware)
            {
                try { sub.Update(); } catch { }
                ProcessSensorSet(h.HardwareType, sub.Sensors, s);
            }
            ProcessSensorSet(h.HardwareType, h.Sensors, s);
        }
    }

    private static void ProcessSensorSet(HardwareType hw, IEnumerable<ISensor> sensors, TelemetrySnapshot s)
    {
        foreach (ISensor sensor in sensors)
        {
            if (sensor.Value is not float raw || float.IsNaN(raw) || float.IsInfinity(raw)) continue;
            bool cpu = hw == HardwareType.Cpu;
            bool gpu = hw == HardwareType.GpuAmd || hw == HardwareType.GpuIntel || hw == HardwareType.GpuNvidia;
            bool battery = hw == HardwareType.Battery;
            string name = sensor.Name.ToLowerInvariant();
            switch (sensor.SensorType)
            {
                case SensorType.Temperature when cpu:
                    if (s.CpuTemp is null || name.Contains("package") || name.Contains("tctl") || name.Contains("tdie"))
                        s.CpuTemp = raw;
                    break;
                case SensorType.Temperature when gpu:
                    if (s.GpuTemp is null || name.Contains("core")) s.GpuTemp = raw;
                    break;
                case SensorType.Power when cpu:
                    if (s.CpuPower is null || name.Contains("package")) s.CpuPower = raw;
                    break;
                case SensorType.Power when gpu:
                    if (s.GpuPower is null || name.Contains("package") || name.Contains("gpu power")) s.GpuPower = raw;
                    break;
                case SensorType.Fan:
                    if (s.FanRpm is null || name.Contains("cpu")) s.FanRpm = raw;
                    break;
                case SensorType.Voltage when battery:
                    s.BatteryVoltage ??= raw;
                    break;
                case SensorType.Power when battery:
                    s.BatteryPower ??= raw;
                    break;
            }
        }
    }

    public void Dispose()
    {
        try { _hardware?.Close(); } catch { }
        _hardware = null;
    }

    [StructLayout(LayoutKind.Sequential)] private struct FileTime
    {
        public uint Low, High;
        public ulong Value => ((ulong)High << 32) | Low;
    }
    [StructLayout(LayoutKind.Sequential)] private struct MemoryStatus
    {
        public uint Length, MemoryLoad;
        public ulong TotalPhysical, AvailablePhysical, TotalPageFile, AvailablePageFile,
            TotalVirtual, AvailableVirtual, AvailableExtendedVirtual;
    }
    [StructLayout(LayoutKind.Sequential)] private struct SystemPowerStatus
    {
        public byte AcLineStatus, BatteryFlag, BatteryLifePercent, SystemStatusFlag;
        public uint BatteryLifeTime, BatteryFullLifeTime;
    }
    [DllImport("kernel32.dll")] [return: MarshalAs(UnmanagedType.Bool)] private static extern bool GetSystemTimes(out FileTime idleTime, out FileTime kernelTime, out FileTime userTime);
    [DllImport("kernel32.dll")] [return: MarshalAs(UnmanagedType.Bool)] private static extern bool GlobalMemoryStatusEx(ref MemoryStatus buffer);
    [DllImport("kernel32.dll")] [return: MarshalAs(UnmanagedType.Bool)] private static extern bool GetSystemPowerStatus(out SystemPowerStatus p);
}
