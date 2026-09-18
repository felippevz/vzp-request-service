package dev.felippevaz.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LoggingConfig {

    private static volatile boolean configured = false;

    private LoggingConfig() {
    }

    public static synchronized void configure() {

        if (configured)
            return;

        Level level = resolveLevel();

        Logger frameworkLogger = Logger.getLogger("dev.felippevaz");
        frameworkLogger.setUseParentHandlers(false);
        frameworkLogger.setLevel(level);

        for (Handler handler : frameworkLogger.getHandlers())
            frameworkLogger.removeHandler(handler);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        consoleHandler.setFormatter(new SimpleLineFormatter());

        frameworkLogger.addHandler(consoleHandler);

        configured = true;
    }

    private static Level resolveLevel() {

        String levelName = System.getProperty("vzp.log.level", "INFO");

        try {
            return Level.parse(levelName.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Level.INFO;
        }
    }

    private static class SimpleLineFormatter extends Formatter {

        @Override
        public String format(LogRecord record) {

            String stack = "";

            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(sw));
                stack = System.lineSeparator() + sw;
            }

            return String.format("%1$tF %1$tT.%1$tL [%2$-7s] %3$s - %4$s%5$s%n",
                    record.getMillis(), record.getLevel().getName(),
                    record.getLoggerName(), formatMessage(record), stack);
        }
    }
}
