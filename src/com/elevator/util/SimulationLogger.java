package com.elevator.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

//класс для логирования событий системы лифтов

public class SimulationLogger {
    private static SimulationLogger instance;
    private final BlockingQueue<LogMessage> logQueue;
    private final SimpleDateFormat timeFormat;
    private PrintWriter fileWriter;
    private volatile boolean running;
    private Thread logThread;

    //внутренний класс для хранения сообщений лога

    private static class LogMessage {
        final String source;
        final String message;
        final long timestamp;
        final LogLevel level;

        LogMessage(String source, String message, LogLevel level) {
            this.source = source;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.level = level;
        }
    }

    public enum LogLevel {
        INFO,      // Информационные сообщения
        WARNING,   // Предупреждения
        ERROR,     // Ошибки
        DEBUG      // Отладочная информация
    }

    private SimulationLogger() {
        this.logQueue = new LinkedBlockingQueue<>();
        this.timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        this.running = false;
        initializeFileWriter();
    }

    public static synchronized SimulationLogger getInstance() {
        if (instance == null) {
            instance = new SimulationLogger();
        }
        return instance;
    }

    private void initializeFileWriter() {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String fileName = "elevator_log_" + timestamp + ".txt";
            fileWriter = new PrintWriter(new FileWriter(fileName, true));
            log("Инициализация логгера. Файл лога: " + fileName, "Logger", LogLevel.INFO);
        } catch (IOException e) {
            System.err.println("Ошибка при создании файла лога: " + e.getMessage());
            fileWriter = null;
        }
    }

    //запуск логгера в отдельном потоке

    public void start() {
        if (running) {
            return;
        }

        running = true;
        logThread = new Thread(this::processLogMessages, "Logger-Thread");
        logThread.setDaemon(true);
        logThread.start();

        log("Логгер запущен", "Logger", LogLevel.INFO);
    }

    //остановка логгера
    public void stop() {
        running = false;
        if (logThread != null) {
            logThread.interrupt();
        }

        //обработка оставшихся сообщений
        processRemainingMessages();

        if (fileWriter != null) {
            fileWriter.close();
        }

        System.out.println("Логгер остановлен");
    }

    private void processLogMessages() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                LogMessage logMessage = logQueue.take();
                writeLogMessage(logMessage);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    //обработка оставшихся сообщений после остановки
    private void processRemainingMessages() {
        while (!logQueue.isEmpty()) {
            LogMessage logMessage = logQueue.poll();
            if (logMessage != null) {
                writeLogMessage(logMessage);
            }
        }
    }

    private synchronized void writeLogMessage(LogMessage logMessage) {
        String timestamp = timeFormat.format(new Date(logMessage.timestamp));
        String logEntry = String.format("[%s] [%s] [%s] %s",
                timestamp,
                logMessage.level,
                logMessage.source,
                logMessage.message);

        //вывод в консоль
        System.out.println(logEntry);

        //запись в файл
        if (fileWriter != null) {
            fileWriter.println(logEntry);
            fileWriter.flush();
        }
    }

    public void info(String source, String message) {
        log(message, source, LogLevel.INFO);
    }

    public void warning(String source, String message) {
        log(message, source, LogLevel.WARNING);
    }

    public void error(String source, String message) {
        log(message, source, LogLevel.ERROR);
    }

    public void debug(String source, String message) {
        log(message, source, LogLevel.DEBUG);
    }

    public void logLiftEvent(int liftId, String event) {
        info("Лифт-" + liftId, event);
    }

    public void logDispatcherEvent(String event) {
        info("Диспетчер", event);
    }

    public void logRequest(String source, String requestInfo) {
        info(source, "Запрос: " + requestInfo);
    }

    public void logStatistics(String statistics) {
        info("Статистика", statistics);
    }

    public void logEmergency(String situation) {
        warning("Внештатная ситуация", situation);
    }

    private void log(String message, String source, LogLevel level) {
        if (!running && level != LogLevel.ERROR) {
            //если логгер не запущен, выводим только ошибки в консоль
            if (level == LogLevel.ERROR) {
                System.err.println("[" + source + "] " + message);
            }
            return;
        }

        LogMessage logMessage = new LogMessage(source, message, level);

        if (running) {
            logQueue.offer(logMessage);
        } else {
            //если логгер остановлен, выводим напрямую
            writeLogMessage(logMessage);
        }
    }

    //проверка, запущен ли логгер
    public boolean isRunning() {
        return running;
    }

    //получение количества сообщений в очереди
    public int getQueueSize() {
        return logQueue.size();
    }
}