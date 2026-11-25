package funlauncher.auth

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.Frame
import java.awt.Window

internal object MicrosoftLoginWindow {
    // The browser starts the protocol handler in a separate process. Let it
    // transfer its foreground permission to the launcher before forwarding the code.
    fun allowForeground(pid: Int) {
        if (Platform.isWindows() && pid > 0) {
            NativeLibrary.getInstance("user32")
                .getFunction("AllowSetForegroundWindow", Function.ALT_CONVENTION)
                .invokeInt(arrayOf(pid))
        }
    }

    fun focus() = EventQueue.invokeLater {
        val frame = Frame.getFrames().firstOrNull { it.isShowing && it.isFocusableWindow } ?: return@invokeLater
        if (frame.extendedState and Frame.ICONIFIED != 0) {
            frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
        }
        val target = generateSequence<Window>(frame) { owner ->
            owner.ownedWindows.lastOrNull { it is Dialog && it.isShowing && it.isFocusableWindow }
        }.last()
        target.toFront()
        target.requestFocus()
    }
}
