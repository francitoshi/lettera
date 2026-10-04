import org.jline.terminal.Terminal;
import org.jline.terminal.Terminal.MouseTracking;
import org.jline.terminal.Terminal.Signal;
import org.jline.terminal.Terminal.SignalHandler;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatTui implements AutoCloseable {

    private static final int INPUT_ROWS = 3;

    private final Terminal terminal;
    private final NonBlockingReader reader;
    private final PrintWriter out;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final LinkedList<AttributedString> chat = new LinkedList<>();
    private final StringBuilder input = new StringBuilder();
    private final List<String> history = new ArrayList<>();

    private int cursor = 0;
    private int scrollOffset = 0;
    private int histIdx = -1;
    private String pending = "";

    public ChatTui() throws IOException {
        terminal = TerminalBuilder.builder().system(true).build();
        terminal.enterRawMode();
        terminal.trackMouse(MouseTracking.Normal);
        terminal.handle(Signal.INT, SignalHandler.SIG_IGN);
        terminal.handle(Signal.WINCH, sig -> dirty.set(true));
        terminal.handle(Signal.CONT, sig -> dirty.set(true));
        reader = terminal.reader();
        out = terminal.writer();
        out.write("\u001b[?7l");
        out.flush();
    }

    public void run() throws IOException {
        new Thread(this::produce).start();
        try {
            while (running.get()) {
                if (dirty.getAndSet(false)) {
                    paint();
                }
                int c = reader.peek(50);
                if (c == -2) continue;
                if (c == -1) break;
                reader.read();
                handleKey(c);
                dirty.set(true);
            }
        } finally {
            close();
        }
    }

    private void handleKey(int c) throws IOException {
        if (c == '\033') {
            int c2 = reader.read();
            if (c2 == -1) return;
            if (c2 == '[') {
                int c3 = reader.read();
                if (c3 == -1) return;
                if (c3 == 'M') {
                    int b = reader.read() - 32;
                    reader.read();
                    reader.read();
                    handleMouse(b);
                } else {
                    handleCsi(csiSuffix(c3));
                }
            } else if (c2 == 'O') {
                int ch = reader.read();
                if (ch == -1) return;
                handleCsi(String.valueOf((char) ch));
            }
        } else if (c == '\r' || c == '\n') {
            submit();
        } else if (c == 127 || c == 8) {
            if (cursor > 0) {
                input.deleteCharAt(cursor - 1);
                cursor--;
            }
        } else if (c == 3) {
            running.set(false);
        } else if (c == 12) {
            synchronized (chat) {
                chat.clear();
            }
            scrollOffset = 0;
        } else if (c >= 32) {
            input.insert(cursor, (char) c);
            cursor++;
        }
    }

    private String csiSuffix(int first) throws IOException {
        StringBuilder seq = new StringBuilder();
        seq.append((char) first);
        while (true) {
            int ch = reader.read();
            if (ch == -1) break;
            seq.append((char) ch);
            if (ch >= 0x40 && ch <= 0x7e) break;
        }
        return seq.toString();
    }

    private void handleCsi(String seq) {
        switch (seq) {
            case "A": historyPrev(); break;
            case "B": historyNext(); break;
            case "C": if (cursor < input.length()) cursor++; break;
            case "D": if (cursor > 0) cursor--; break;
            case "H": cursor = 0; break;
            case "F": cursor = input.length(); break;
            case "3~": if (cursor < input.length()) input.deleteCharAt(cursor); break;
            case "5~": pageUp(); break;
            case "6~": pageDown(); break;
            default: break;
        }
    }

    private void handleMouse(int b) {
        if ((b & 64) != 0) {
            if ((b & 3) == 0) {
                scrollBy(3);
            } else {
                scrollBy(-3);
            }
        }
    }

    private void submit() {
        String text = input.toString().trim();
        if (text.isEmpty()) return;
        history.add(text);
        histIdx = -1;
        int w = terminal.getWidth();
        addLine("> " + text,
                AttributedStyle.DEFAULT.bold().foreground(AttributedStyle.GREEN), w);
        input.setLength(0);
        cursor = 0;
    }

    private void historyPrev() {
        if (history.isEmpty()) return;
        if (histIdx == -1) {
            pending = input.toString();
            histIdx = history.size() - 1;
        } else if (histIdx > 0) {
            histIdx--;
        } else {
            return;
        }
        setInput(history.get(histIdx));
    }

    private void historyNext() {
        if (histIdx == -1) return;
        histIdx++;
        if (histIdx >= history.size()) {
            histIdx = -1;
            setInput(pending);
        } else {
            setInput(history.get(histIdx));
        }
    }

    private void setInput(String s) {
        input.setLength(0);
        input.append(s);
        cursor = input.length();
    }

    private int chatHeight() {
        return Math.max(1, terminal.getHeight() - INPUT_ROWS);
    }

    private void pageUp() {
        scrollBy(chatHeight());
    }

    private void pageDown() {
        scrollBy(-chatHeight());
    }

    private void scrollBy(int n) {
        int h = chatHeight();
        synchronized (chat) {
            int max = Math.max(0, chat.size() - h);
            scrollOffset = Math.max(0, Math.min(max, scrollOffset + n));
        }
    }

    private void addLine(String text, AttributedStyle style, int width) {
        synchronized (chat) {
            chat.addAll(wrap(text, style, Math.max(10, width)));
        }
    }

    private static List<AttributedString> wrap(String text, AttributedStyle style, int width) {
        List<AttributedString> lines = new ArrayList<>();
        AttributedString as = new AttributedString(text, style);
        int len = as.columnLength();
        for (int i = 0; i < len; i += width) {
            lines.add(as.substring(i, Math.min(len, i + width)));
        }
        return lines;
    }

    private void paint() {
        int h = terminal.getHeight();
        int w = terminal.getWidth();
        if (h < INPUT_ROWS + 3 || w < 12) return;
        int chatH = h - INPUT_ROWS;

        synchronized (chat) {
            int total = chat.size();
            int maxScroll = Math.max(0, total - chatH);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            int start = Math.max(0, total - chatH - scrollOffset);

            StringBuilder sb = new StringBuilder();
            sb.append("\u001b[?25l");
            sb.append("\u001b[H");
            int line = 0;
            for (int i = start; i < total; i++) {
                AttributedString s = chat.get(i);
                sb.append(s.columnLength() > w ? s.substring(0, w).toAnsi() : s.toAnsi());
                sb.append("\r\n");
                line++;
            }
            for (; line < chatH; line++) {
                sb.append("\r\n");
            }
            sb.append(border('┌', '─', '┐', w)).append("\r\n");
            sb.append(inputLine(w)).append("\r\n");
            sb.append(border('└', '─', '┘', w));
            sb.append("\u001b[0J");
            sb.append("\u001b[").append(h - 1).append(';').append(5 + cursorWindowOffset(w)).append('H');
            sb.append("\u001b[?25h");
            out.write(sb.toString());
            out.flush();
        }
    }

    private static String border(char tl, char hz, char tr, int w) {
        StringBuilder sb = new StringBuilder(w);
        sb.append(tl);
        for (int i = 0; i < w - 2; i++) sb.append(hz);
        sb.append(tr);
        return sb.toString();
    }

    private String inputLine(int w) {
        int cw = w - 6;
        String text = input.toString();
        int start = Math.max(0, cursor - cw + 1);
        int end = Math.min(text.length(), start + cw);
        String view = text.substring(start, end);
        StringBuilder sb = new StringBuilder(w);
        sb.append("│ > ");
        sb.append(view);
        for (int i = view.length(); i < cw; i++) sb.append(' ');
        sb.append(" │");
        return sb.toString();
    }

    private int cursorWindowOffset(int w) {
        int cw = w - 6;
        String text = input.toString();
        int start = Math.max(0, cursor - cw + 1);
        return cursor - start;
    }

    private void produce() {
        int i = 0;
        while (running.get()) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                return;
            }
            int w = terminal.getWidth();
            addLine("bot> mensaje de prueba " + (i++) + " xxxxxxxxxxxxxxxxxxxx",
                    AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN), w);
            dirty.set(true);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        running.set(false);
        try {
            terminal.trackMouse(MouseTracking.Off);
            out.write("\u001b[?7h\u001b[2J\u001b[H\u001b[0m");
            out.flush();
            terminal.close();
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        ChatTui tui = new ChatTui();
        try {
            tui.run();
        } finally {
            tui.close();
        }
    }
}
