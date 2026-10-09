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

    // Derivado da própria classe (e não "dev.felippevaz" fixo) para continuar
    // funcionando quando o framework é relocado via shade dentro de outro jar.
    private static final String ROOT_LOGGER = rootPackage();

    private static volatile boolean configured = false;

    private LoggingConfig() {
    }

    public static synchronized void configure() {

        if (configured)
            return;

        Level level = resolveLevel();

        Logger frameworkLogger = Logger.getLogger(ROOT_LOGGER);
        frameworkLogger.setUseParentHandlers(false);
        frameworkLogger.setLevel(level);

        removeHandlers(frameworkLogger);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(level);
        consoleHandler.setFormatter(new SimpleLineFormatter());

        frameworkLogger.addHandler(consoleHandler);

        configured = true;
    }

    /**
     * Envia todos os logs do framework para outro logger (ex.: o logger de um plugin
     * Bukkit), em vez do console próprio. Pode ser chamado antes ou depois de
     * criar o {@code RequestServer}.
     */
    public static synchronized void redirectTo(Logger target) {

        Logger frameworkLogger = Logger.getLogger(ROOT_LOGGER);
        frameworkLogger.setUseParentHandlers(false);
        frameworkLogger.setLevel(resolveLevel());

        removeHandlers(frameworkLogger);

        frameworkLogger.addHandler(new ForwardingHandler(target));

        configured = true;
    }

    public static String getRootLoggerName() {
        return ROOT_LOGGER;
    }

    private static void removeHandlers(Logger logger) {
        for (Handler handler : logger.getHandlers())
            logger.removeHandler(handler);
    }

    private static Level resolveLevel() {

        String levelName = System.getProperty("vzp.log.level", "INFO");

        try {
            return Level.parse(levelName.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return Level.INFO;
        }
    }

    private static String rootPackage() {

        String name = LoggingConfig.class.getName();
        String suffix = ".logging.LoggingConfig";

        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : "dev.felippevaz";
    }

    private static class ForwardingHandler extends Handler {

        private final Logger target;

        ForwardingHandler(Logger target) {
            this.target = target;
        }

        @Override
        public void publish(LogRecord record) {
            // target.log(record) (e não o handler do target) para que loggers que
            // decoram a mensagem, como o PluginLogger do Bukkit, apliquem o prefixo.
            if (target.isLoggable(record.getLevel()))
                target.log(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
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
