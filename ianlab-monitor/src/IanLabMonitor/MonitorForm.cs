using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace IanLabMonitor;

internal sealed class MonitorForm : Form
{
    private const int WidthHud = 835, HeightHud = 70, HeightExpanded = 515;
    private static readonly Color Back = Color.FromArgb(10, 17, 36);
    private static readonly Color Surface = Color.FromArgb(21, 33, 62);
    private static readonly Color Outline = Color.FromArgb(92, 80, 156);
    private static readonly Color White = Color.FromArgb(232, 242, 255);
    private static readonly Color Muted = Color.FromArgb(145, 166, 202);
    private static readonly Color Blue = Color.FromArgb(69, 186, 255);
    private static readonly Color Purple = Color.FromArgb(184, 103, 255);
    private static readonly Color Green = Color.FromArgb(62, 226, 150);
    private static readonly Color Orange = Color.FromArgb(255, 170, 87);
    private static readonly Font FontTitle = new("Segoe UI Semibold", 12.5f);
    private static readonly Font FontHeading = new("Segoe UI Semibold", 10.5f);
    private static readonly Font FontBody = new("Segoe UI", 9.3f);
    private static readonly Font FontTiny = new("Segoe UI", 8.3f);
    private static readonly Font FontBig = new("Segoe UI Semibold", 18f);

    private readonly MonitorSettings _settings = MonitorSettings.Load();
    private readonly Telemetry _telemetry = new();
    private readonly AudioControl _audio = new();
    private readonly System.Windows.Forms.Timer _pollTimer = new() { Interval = 2000 };
    private readonly NotifyIcon _tray;
    private TelemetrySnapshot _snapshot = new();
    private readonly Queue<float> _cpuHistory = new();
    private readonly Queue<float> _gpuHistory = new();
    private bool _expanded;
    private bool _sampling;
    private bool _closing;
    private bool _dragging;
    private bool _adjustingVolume;
    private int _previewVolume = -1;
    private Point _startMouse, _startWindow;
    private const int HotkeyId = 0x1A1A;
    private bool _hotkeyRegistered;

    public MonitorForm()
    {
        Text = "IANLAB Monitor";
        ClientSize = new Size(WidthHud, HeightHud);
        FormBorderStyle = FormBorderStyle.None;
        ShowInTaskbar = false;
        StartPosition = FormStartPosition.Manual;
        DoubleBuffered = true;
        BackColor = Back;
        TopMost = _settings.TopMost;
        Font = FontBody;
        AutoScaleMode = AutoScaleMode.Dpi;
        MinimumSize = new Size(WidthHud, HeightHud);
        MaximumSize = new Size(WidthHud, HeightExpanded);
        SetInitialPosition();

        ContextMenuStrip menu = new();
        menu.Items.Add("Hiện / Ẩn (Ctrl+Alt+M)", null, (_, _) => ToggleVisibility());
        menu.Items.Add("Mở bảng chi tiết", null, (_, _) => { ShowHud(); Expand(true); });
        menu.Items.Add("Luôn trên cùng", null, (_, _) => { _settings.TopMost = !_settings.TopMost; TopMost = _settings.TopMost; _settings.Save(); Invalidate(); });
        menu.Items.Add("Bật / Tắt bấm xuyên qua", null, (_, _) => ToggleClickThrough());
        ToolStripMenuItem adapter = new("Công suất sạc (định mức)");
        foreach (int watts in new[] { 0, 135, 170 })
        {
            int selection = watts;
            adapter.DropDownItems.Add(watts == 0 ? "Không rõ" : $"{watts} W", null, (_, _) =>
            {
                _settings.AdapterWatts = selection;
                _settings.Save();
                Invalidate();
            });
        }
        menu.Items.Add(adapter);
        menu.Items.Add(new ToolStripSeparator());
        menu.Items.Add("Thoát IANLAB Monitor", null, (_, _) => { _closing = true; Close(); });
        _tray = new NotifyIcon
        {
            Icon = SystemIcons.Application,
            Text = "IANLAB Monitor - Ctrl+Alt+M",
            Visible = true,
            ContextMenuStrip = menu
        };
        _tray.DoubleClick += (_, _) => ToggleVisibility();
        _pollTimer.Tick += async (_, _) => await PollAsync();
        Shown += async (_, _) =>
        {
            _pollTimer.Start();
            if (_settings.ClickThrough) ApplyClickThrough(true);
            await PollAsync();
        };
    }

    private void SetInitialPosition()
    {
        Rectangle w = Screen.PrimaryScreen?.WorkingArea ?? new Rectangle(0, 0, 1366, 768);
        int x = _settings.Left == int.MinValue ? w.Right - WidthHud - 14 : _settings.Left;
        int y = _settings.Top == int.MinValue ? w.Top + 12 : _settings.Top;
        Location = new Point(Math.Clamp(x, w.Left, Math.Max(w.Left, w.Right - 120)),
            Math.Clamp(y, w.Top, Math.Max(w.Top, w.Bottom - HeightHud)));
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        _hotkeyRegistered = RegisterHotKey(Handle, HotkeyId, 0x0001 | 0x0002, (uint)Keys.M);
    }
    protected override void OnHandleDestroyed(EventArgs e)
    {
        if (_hotkeyRegistered) { UnregisterHotKey(Handle, HotkeyId); _hotkeyRegistered = false; }
        base.OnHandleDestroyed(e);
    }
    protected override void WndProc(ref Message m)
    {
        if (m.Msg == 0x0312 && m.WParam.ToInt32() == HotkeyId) { ToggleVisibility(); return; }
        base.WndProc(ref m);
    }

    private async Task PollAsync()
    {
        if (_sampling || _closing || IsDisposed) return;
        _sampling = true;
        try
        {
            TelemetrySnapshot next = await Task.Run(() => _telemetry.Read());
            if (_closing || IsDisposed) return;
            _snapshot = next;
            AddHistory(_cpuHistory, next.CpuTemp);
            AddHistory(_gpuHistory, next.GpuTemp);
            Invalidate();
        }
        catch { }
        finally { _sampling = false; }
    }
    private static void AddHistory(Queue<float> queue, float? value)
    {
        queue.Enqueue(value ?? float.NaN);
        while (queue.Count > 70) queue.Dequeue();
    }

    private void ShowHud()
    {
        if (!Visible) Show();
        WindowState = FormWindowState.Normal;
        TopMost = _settings.TopMost;
        Activate();
    }
    private void ToggleVisibility() { if (Visible) Hide(); else ShowHud(); }
    private void Expand(bool expand)
    {
        if (_expanded == expand) return;
        _expanded = expand;
        ClientSize = new Size(WidthHud, expand ? HeightExpanded : HeightHud);
        Invalidate();
    }
    private void ToggleClickThrough()
    {
        _settings.ClickThrough = !_settings.ClickThrough;
        ApplyClickThrough(_settings.ClickThrough);
        _settings.Save();
        Invalidate();
        if (_settings.ClickThrough) _tray.ShowBalloonTip(1600, "IANLAB Monitor", "Bấm xuyên qua đã bật. Ctrl+Alt+M hoặc menu khay hệ thống để điều khiển.", ToolTipIcon.Info);
    }
    private void ApplyClickThrough(bool enable)
    {
        if (!IsHandleCreated) return;
        const int GwlExStyle = -20;
        const long WsExTransparent = 0x20, WsExLayered = 0x80000;
        IntPtr old = GetWindowLongPtr(Handle, GwlExStyle);
        long flags = old.ToInt64();
        flags = enable ? (flags | WsExTransparent | WsExLayered) : (flags & ~WsExTransparent);
        SetWindowLongPtr(Handle, GwlExStyle, new IntPtr(flags));
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        base.OnPaint(e);
        Graphics g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.ClearTypeGridFit;
        using (var b = new LinearGradientBrush(ClientRectangle, Color.FromArgb(17, 23, 53), Color.FromArgb(9, 19, 43), 0f))
            g.FillRectangle(b, ClientRectangle);
        using (var p = new Pen(Outline, 1.3f))
        { g.DrawLine(p, 0, 1, WidthHud - 1, 1); g.DrawLine(p, 0, Height - 2, WidthHud - 1, Height - 2); }
        PaintHud(g);
        if (_expanded) PaintDetails(g);
    }

    private void PaintHud(Graphics g)
    {
        DrawText(g, "◆", 17, 12, 35, 32, Purple, FontBig);
        DrawText(g, "IANLAB", 49, 10, 105, 22, White, FontTitle);
        DrawText(g, "Monitor", 50, 32, 95, 24, Muted, FontBody);
        DrawChip(g, new Rectangle(164, 9, 112, 50), "CPU", Number(_snapshot.CpuTemp, "°C"), Blue, _snapshot.CpuLoad);
        DrawChip(g, new Rectangle(283, 9, 112, 50), "GPU", Number(_snapshot.GpuTemp, "°C"), Green, _snapshot.GpuTemp.HasValue ? Math.Clamp(_snapshot.GpuTemp.Value, 0, 100) : 0);
        DrawChip(g, new Rectangle(402, 9, 126, 50), "RAM", $"{_snapshot.RamPercent:0}%", Purple, _snapshot.RamPercent);
        DrawChip(g, new Rectangle(535, 9, 103, 50), "PIN", _snapshot.BatteryPercent < 0 ? "N/A" : $"{_snapshot.BatteryPercent}%", Green, Math.Max(0, _snapshot.BatteryPercent));
        DrawChip(g, new Rectangle(645, 9, 91, 50), "SẠC", _snapshot.OnAc ? "AC" : "Pin", Purple, _snapshot.OnAc ? 100 : 30);
        Rounded(g, new Rectangle(743, 10, 55, 49), 13, Surface, Outline);
        DrawText(g, _previewVolume >= 0 ? $"{_previewVolume}%" : _audio.VolumePercent < 0 ? "N/A" : $"{_audio.VolumePercent}%", 748, 17, 45, 21, White, FontBody, true);
        using (var p = new Pen(Blue, 3f))
        {
            int vol = _previewVolume >= 0 ? _previewVolume : Math.Max(0, _audio.VolumePercent);
            using (var mutedPen = new Pen(Color.FromArgb(48, 63, 97), 3f))
                g.DrawLine(mutedPen, 751, 46, 789, 46);
            g.DrawLine(p, 751, 46, 751 + vol * 38 / 100, 46);
        }
        Rounded(g, new Rectangle(804, 12, 27, 44), 10, Surface, Outline);
        DrawText(g, _expanded ? "⌃" : "⌄", 807, 20, 21, 23, White, FontHeading, true);
    }

    private void DrawChip(Graphics g, Rectangle rect, string label, string value, Color accent, float pct)
    {
        Rounded(g, rect, 12, Surface, Color.FromArgb(52, 74, 118));
        DrawText(g, label, rect.X + 10, rect.Y + 5, rect.Width - 12, 19, Muted, FontTiny);
        DrawText(g, value, rect.X + 10, rect.Y + 23, rect.Width - 12, 18, White, FontHeading);
        using Pen p = new(accent, 3f);
        g.DrawLine(p, rect.Right - 40, rect.Bottom - 9, rect.Right - 40 + (int)(Math.Clamp(pct, 0, 100) * 26 / 100), rect.Bottom - 9);
    }

    private void PaintDetails(Graphics g)
    {
        DrawText(g, "NHIỆT ĐỘ & HIỆU NĂNG", 24, 86, 345, 30, White, FontHeading);
        Rounded(g, new Rectangle(20, 120, 388, 224), 16, Surface, Outline);
        DrawText(g, "CPU", 36, 133, 75, 23, Muted, FontBody);
        DrawText(g, Number(_snapshot.CpuTemp, "°C"), 98, 129, 116, 30, Blue, FontBig);
        DrawText(g, $"Tải: {_snapshot.CpuLoad:0}%", 250, 133, 135, 26, White, FontBody);
        DrawGraph(g, new Rectangle(34, 170, 357, 57), _cpuHistory, Blue);
        DrawText(g, "GPU RTX", 36, 235, 88, 23, Muted, FontBody);
        DrawText(g, Number(_snapshot.GpuTemp, "°C"), 129, 231, 108, 30, Green, FontBig);
        DrawGraph(g, new Rectangle(34, 271, 357, 57), _gpuHistory, Green);

        Rounded(g, new Rectangle(424, 120, 391, 224), 16, Surface, Outline);
        DrawText(g, "ĐIỆN NĂNG & PIN", 440, 132, 338, 28, White, FontHeading);
        DrawKeyValue(g, "Nguồn cấp", _snapshot.OnAc ? "Đang cắm sạc" : "Dùng pin", 443, 162, _snapshot.OnAc ? Green : Orange);
        DrawKeyValue(g, "Adapter (định mức)", "20V DC — KHÔNG PHẢI SỐ ĐO", 443, 191, Purple);
        DrawKeyValue(g, "Công suất sạc", _settings.AdapterWatts == 0 ? "Chưa chọn 135/170W" : $"{_settings.AdapterWatts}W (định mức)", 443, 220, White);
        DrawKeyValue(g, "CPU / GPU Power", $"{Number(_snapshot.CpuPower, "W")} / {Number(_snapshot.GpuPower, "W")}", 443, 249, White);
        DrawKeyValue(g, "Điện áp pin thực", Number(_snapshot.BatteryVoltage, "V"), 443, 278, White);
        DrawText(g, "Không đo được điện áp trực tiếp tại cổng adapter bằng phần mềm thông thường.", 442, 307, 365, 25, Muted, FontTiny);

        Rounded(g, new Rectangle(20, 358, 388, 126), 16, Surface, Outline);
        DrawText(g, "QUẠT & LÀM MÁT", 35, 371, 320, 24, White, FontHeading);
        DrawKeyValue(g, "Tốc độ quạt", Number(_snapshot.FanRpm, "RPM"), 36, 406, Blue);
        DrawText(g, "Quạt do firmware Lenovo quản lý. Fn+Q chuyển chế độ máy.", 36, 449, 357, 22, Muted, FontTiny);

        Rounded(g, new Rectangle(424, 358, 391, 126), 16, Surface, Outline);
        DrawText(g, "HỆ THỐNG", 440, 371, 345, 25, White, FontHeading);
        DrawKeyValue(g, "RAM", $"{_snapshot.UsedMemory / 1073741824d:0.0} / {_snapshot.TotalMemory / 1073741824d:0.0} GB", 440, 402, White);
        DrawKeyValue(g, "Wi-Fi", _snapshot.Wifi.Length > 19 ? _snapshot.Wifi[..19] + "…" : _snapshot.Wifi, 440, 428, Green);
        DrawKeyValue(g, "Tốc độ nhận", $"{_snapshot.NetDownMbps:0.00} Mbps", 440, 455, White);

        DrawText(g, _snapshot.Error.Length > 0 ? _snapshot.Error : "Tần suất lấy mẫu 2 giây • Không can thiệp BIOS / tốc độ quạt", 20, 489, 700, 22,
            _snapshot.Error.Length > 0 ? Orange : Muted, FontTiny);
        DrawText(g, _settings.ClickThrough ? "Xuyên chuột: ON" : "Xuyên chuột: OFF", 685, 489, 143, 22, Purple, FontTiny, true);
    }
    private static void DrawKeyValue(Graphics g, string key, string value, int x, int y, Color valueColor)
    {
        DrawText(g, key, x, y, 166, 24, Muted, FontBody);
        DrawText(g, value, x + 160, y, 215, 24, valueColor, FontBody);
    }
    private static void DrawGraph(Graphics g, Rectangle rect, IEnumerable<float> values, Color lineColor)
    {
        using Pen grid = new(Color.FromArgb(58, 78, 119), 1f);
        g.DrawLine(grid, rect.X, rect.Y + rect.Height / 2, rect.Right, rect.Y + rect.Height / 2);
        g.DrawLine(grid, rect.X, rect.Bottom, rect.Right, rect.Bottom);
        float[] arr = new List<float>(values).ToArray();
        if (arr.Length < 2) return;
        using Pen curve = new(lineColor, 2f);
        for (int i = 1; i < arr.Length; i++)
        {
            if (float.IsNaN(arr[i]) || float.IsNaN(arr[i - 1])) continue;
            float x1 = rect.X + (i - 1) * rect.Width / Math.Max(arr.Length - 1, 1);
            float x2 = rect.X + i * rect.Width / Math.Max(arr.Length - 1, 1);
            float y1 = rect.Bottom - Math.Clamp(arr[i - 1], 20, 105) / 110 * rect.Height;
            float y2 = rect.Bottom - Math.Clamp(arr[i], 20, 105) / 110 * rect.Height;
            g.DrawLine(curve, x1, y1, x2, y2);
        }
    }
    private static string Number(float? value, string suffix) => value.HasValue ? $"{value.Value:0.#}{suffix}" : "N/A";
    private static void DrawText(Graphics g, string text, int x, int y, int w, int h, Color color, Font font, bool center = false)
    {
        TextRenderer.DrawText(g, text, font, new Rectangle(x, y, w, h), color,
            TextFormatFlags.EndEllipsis | TextFormatFlags.VerticalCenter | (center ? TextFormatFlags.HorizontalCenter : TextFormatFlags.Left));
    }
    private static void Rounded(Graphics g, Rectangle rect, int rad, Color fill, Color outline)
    {
        using GraphicsPath path = new();
        int d = rad * 2;
        path.AddArc(rect.X, rect.Y, d, d, 180, 90);
        path.AddArc(rect.Right - d, rect.Y, d, d, 270, 90);
        path.AddArc(rect.Right - d, rect.Bottom - d, d, d, 0, 90);
        path.AddArc(rect.X, rect.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        using SolidBrush b = new(fill);
        using Pen p = new(outline, 1f);
        g.FillPath(b, path);
        g.DrawPath(p, path);
    }

    private static bool In(Rectangle r, Point p) => r.Contains(p);
    private static readonly Rectangle ToggleRect = new(799, 7, 36, 54);
    private static readonly Rectangle VolumeRect = new(743, 9, 56, 52);
    protected override void OnMouseDown(MouseEventArgs e)
    {
        base.OnMouseDown(e);
        if (e.Button == MouseButtons.Right) { _tray.ContextMenuStrip?.Show(Cursor.Position); return; }
        if (e.Button != MouseButtons.Left) return;
        if (In(VolumeRect, e.Location)) { _adjustingVolume = true; UpdateVolumePreview(e.X); Capture = true; return; }
        if (In(ToggleRect, e.Location)) { Expand(!_expanded); return; }
        if (e.Y < HeightHud) { _dragging = true; _startMouse = Cursor.Position; _startWindow = Location; Capture = true; }
    }
    protected override void OnMouseMove(MouseEventArgs e)
    {
        base.OnMouseMove(e);
        if (_adjustingVolume) { UpdateVolumePreview(e.X); return; }
        if (_dragging)
        {
            Point delta = new(Cursor.Position.X - _startMouse.X, Cursor.Position.Y - _startMouse.Y);
            Location = new Point(_startWindow.X + delta.X, _startWindow.Y + delta.Y);
        }
    }
    protected override void OnMouseUp(MouseEventArgs e)
    {
        base.OnMouseUp(e);
        if (_adjustingVolume)
        {
            _adjustingVolume = false;
            if (_previewVolume >= 0) _audio.SetVolume(_previewVolume);
            _previewVolume = -1;
            Capture = false;
            Invalidate();
        }
        if (_dragging)
        {
            _dragging = false;
            Capture = false;
            _settings.Left = Left;
            _settings.Top = Top;
            _settings.Save();
        }
    }
    private void UpdateVolumePreview(int x)
    {
        _previewVolume = Math.Clamp((x - 750) * 100 / 42, 0, 100);
        Invalidate(VolumeRect);
    }
    protected override void OnMouseWheel(MouseEventArgs e)
    {
        base.OnMouseWheel(e);
        int curr = _audio.VolumePercent;
        if (curr >= 0) { _audio.SetVolume(curr + (e.Delta > 0 ? 5 : -5)); Invalidate(); }
    }
    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (!_closing && e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            Hide();
            return;
        }
        base.OnFormClosing(e);
    }
    protected override void OnFormClosed(FormClosedEventArgs e)
    {
        _closing = true;
        _pollTimer.Stop(); _pollTimer.Dispose();
        _tray.Visible = false; _tray.Dispose();
        _audio.Dispose(); _telemetry.Dispose();
        base.OnFormClosed(e);
    }

    [DllImport("user32.dll", SetLastError = true)] [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);
    [DllImport("user32.dll", SetLastError = true)] [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool UnregisterHotKey(IntPtr hWnd, int id);
    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW", SetLastError = true)]
    private static extern IntPtr GetWindowLongPtr(IntPtr hWnd, int nIndex);
    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtrW", SetLastError = true)]
    private static extern IntPtr SetWindowLongPtr(IntPtr hWnd, int nIndex, IntPtr dwNewLong);
}
