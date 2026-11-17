using System;
using System.Threading;
using System.Windows.Forms;

namespace IanLabMonitor;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        using Mutex instance = new(true, "Local\\IANLAB-Monitor-UI-v1", out bool first);
        if (!first)
        {
            MessageBox.Show("IANLAB Monitor đang chạy. Mở từ khay hệ thống hoặc nhấn Ctrl+Alt+M.",
                "IANLAB Monitor", MessageBoxButtons.OK, MessageBoxIcon.Information);
            return;
        }
        ApplicationConfiguration.Initialize();
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, args) =>
        {
            MessageBox.Show($"Ứng dụng gặp lỗi: {args.Exception.Message}", "IANLAB Monitor",
                MessageBoxButtons.OK, MessageBoxIcon.Warning);
        };
        Application.Run(new MonitorForm());
    }
}
