package com.showdownrpc.platform;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects whether the Showdown desktop app's teambuilder is open, by reading the
 * foreground window's title. Windows-only; on any other OS inTeambuilder() is
 * always false. Fully isolated — a failure here must never affect the protocol path.
 */
public class WindowWatcher {
    private static final Logger log = LoggerFactory.getLogger(WindowWatcher.class);
    private static final String TEAMBUILDER_TITLE = "Teambuilder - Showdown!";
    private static final boolean IS_WINDOWS =
        System.getProperty("os.name", "").toLowerCase().contains("win");

    public boolean inTeambuilder() {
        if (!IS_WINDOWS) return false;
        try {
            HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) return false;
            char[] buffer = new char[512];
            User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
            String title = Native.toString(buffer);
            return TEAMBUILDER_TITLE.equals(title);
        } catch (Throwable t) {
            // Never let a native call take down the presence loop.
            log.debug("WindowWatcher failed; treating as not-in-teambuilder", t);
            return false;
        }
    }
}
