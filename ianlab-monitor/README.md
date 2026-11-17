# IANLAB Monitor (Preview 0.1) — Windows lightweight overlay

A low-overhead, **native Windows Forms + GDI+** monitoring overlay written in C# (.NET 8) for Lenovo IdeaPad Gaming 3 2021-class laptops. Uses **LibreHardwareMonitorLib 0.9.6** to read CPU/GPU temperature, power, accessible fan RPM, and battery voltage (when a sensor actually exposes it). Uses Win32 APIs for CPU load, RAM and battery, network-interface counters for Wi-Fi, and Core Audio for volume.

## Included

- Always-on-top floating HUD; expand/collapse detailed panels; move by dragging the header.
- CPU load, CPU/GPU temperatures, RAM, GPU/CPU power when supported; 2-second background sensor sampling.
- Historical temperature sparkline; battery %, AC vs battery, Wi-Fi speed (Mbps).
- Click-through mode toggled from **system tray**; global **Ctrl+Alt+M** hotkey shows/hides HUD.
- Windows system volume control by dragging the small top-right slider (changes only on release).
- Charger nameplate rating setting: choose **135 W / 170 W / Unknown** in tray; explicitly labeled **nominal**. Adapter output commonly 20 V DC nominal, **NOT a live sensor reading**.
- Fan speed **read-only**, displays N/A if Lenovo firmware doesn't expose it. NO fan speed control, voltage writes, embedded-controller hacking, or unsafe hidden BIOS overrides.
- Single-instance, no advertisements, no WebView/Electron, no online telemetry, no auto-updaters, no FPS monitoring.

## Build

**Windows 10/11 x64**, .NET SDK 8 installed. Open a PowerShell terminal at repository root and run:

```powershell
.\build-windows.ps1
```

This publishes `dist/IANLAB-Monitor-win-x64.zip` (portable **self-contained**, no .NET runtime installation needed). Extract ALL files into a folder; run `IANLAB.Monitor.exe` there.

For a smaller disk package if you already have the **.NET 8 Desktop Runtime** installed:

```powershell
.\build-windows.ps1 -FrameworkDependent
```

When built by GitHub Actions, download from the run's **Artifacts** section or tagged pre-release.

## Minimal RAM strategy

WinForms/GDI+, no embedded browser, 2-second polling in one background task (skips overlapping samples), bounded 70-point graphs, no animated 60 FPS UI, no external background agents. **RAM usage depends on .NET runtime and available sensors**, and has not been measured on the target IdeaPad yet. Use Windows Task Manager -> Details -> Working set / Memory to validate and compare. A self-contained package is larger **on disk**, not intrinsically larger in active RAM.

## Lenovo IdeaPad Gaming 3 2021 notes

- Fn+Q requests a Lenovo thermal/performance profile change through laptop firmware. This app does not force fan RPM or power limits.
- On some Lenovo models, embedded-controller fan speed is not visible to LibreHardwareMonitor. N/A is intentional.
- CPU/GPU temperature reads may require Administrator privileges. This app launches **without elevation** to minimize privileges. If CPU temperature is N/A, right-click -> Run as administrator to test. This does not guarantee access.
- **No Windows software can know the actual power-supply input voltage/current if the laptop does not expose a sensor.** 20V is a typical adapter nameplate voltage, not a measured reading. CPU/GPU power does not equal entire wall power. Use an external AC power meter to measure wall draw or a suitable DC power meter for adapter output.
- If Nvidia GPU is asleep, the GPU reading may be missing until the GPU activates. Avoid polling GPU every few milliseconds.

## UI interactions

- Drag the top bar (except volume/expand button) to move.
- Click chevron on right to expand/collapse.
- Drag the volume section next to the chevron to adjust Windows master output volume.
- Mouse wheel over HUD: volume ±5%.
- Double click tray icon or press Ctrl+Alt+M: show/hide.
- Right-click the HUD or tray icon for adapter rating, always-on-top, click-through and exit.
- To disable click-through, use tray menu; click-through prevents clicking the HUD by design.

## Source / licensing

App code is original and may be modified. Third-party libraries: LibreHardwareMonitorLib (MPL-2.0) and NAudio (MIT), distributed via NuGet. See each dependency's license and attribution details. This is **test software**, not an OEM diagnostic or hardware safety controller.
