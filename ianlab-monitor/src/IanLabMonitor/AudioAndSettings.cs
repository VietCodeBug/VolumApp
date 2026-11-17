using System;
using System.IO;
using System.Text.Json;
using NAudio.CoreAudioApi;

namespace IanLabMonitor;

internal sealed class AudioControl : IDisposable
{
    private MMDeviceEnumerator? _enumerator;
    private MMDevice? _device;

    public int VolumePercent
    {
        get
        {
            try { Ensure(); return (int)Math.Round((_device?.AudioEndpointVolume.MasterVolumeLevelScalar ?? 0f) * 100); }
            catch { return -1; }
        }
    }
    public bool Muted
    {
        get { try { Ensure(); return _device?.AudioEndpointVolume.Mute ?? false; } catch { return false; } }
    }
    public void SetVolume(int percent)
    {
        try
        {
            Ensure();
            if (_device != null)
                _device.AudioEndpointVolume.MasterVolumeLevelScalar = Math.Clamp(percent, 0, 100) / 100f;
        }
        catch { Reopen(); }
    }
    public void ToggleMute()
    {
        try { Ensure(); if (_device != null) _device.AudioEndpointVolume.Mute = !_device.AudioEndpointVolume.Mute; }
        catch { Reopen(); }
    }
    private void Ensure()
    {
        if (_device != null) return;
        _enumerator = new MMDeviceEnumerator();
        _device = _enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
    }
    private void Reopen()
    {
        try { _device?.Dispose(); } catch { }
        try { _enumerator?.Dispose(); } catch { }
        _device = null;
        _enumerator = null;
    }
    public void Dispose() => Reopen();
}

internal sealed class MonitorSettings
{
    public bool TopMost { get; set; } = true;
    public bool ClickThrough { get; set; }
    public int AdapterWatts { get; set; } // 0 = unknown; never assume OEM charger rating.
    public int Left { get; set; } = int.MinValue;
    public int Top { get; set; } = int.MinValue;

    private static string FileName => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "IANLAB", "Monitor", "settings.json");

    public static MonitorSettings Load()
    {
        try { if (File.Exists(FileName)) return JsonSerializer.Deserialize<MonitorSettings>(File.ReadAllText(FileName)) ?? new(); }
        catch { }
        return new MonitorSettings();
    }
    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FileName)!);
            File.WriteAllText(FileName, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch { }
    }
}
