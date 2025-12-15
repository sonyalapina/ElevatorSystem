package com.elevator.core;

import com.elevator.model.Direction;
import com.elevator.model.Request;
import com.elevator.util.SimulationLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

//диспетчер системы лифтов
//распределяет запросы между лифтами по оптимальному алгоритму

public class Dispatcher implements Runnable {
    private final List<Lift> lifts;                    //список всех лифтов в системе
    private final BlockingQueue<Request> requestQueue;
    private final AtomicInteger totalRequests;         //счетчик всех обработанных запросов
    private final int maxFloor;
    private final int minFloor = 1;
    private volatile boolean running;                  //флаг работы диспетчера
    private final SimulationLogger logger;

    public Dispatcher(int numberOfLifts, int maxFloor) {
        this.maxFloor = maxFloor;
        this.lifts = new CopyOnWriteArrayList<>();
        this.requestQueue = new LinkedBlockingQueue<>();
        this.totalRequests = new AtomicInteger(0);
        this.running = true;
        this.logger = SimulationLogger.getInstance();

        initializeLifts(numberOfLifts); //инициализация лифтов
    }

    //инициализирует лифты и запускает их в отдельных потоках
    //лифты равномерно распределяются по этажам при старте

    private void initializeLifts(int numberOfLifts) {
        for (int i = 0; i < numberOfLifts; i++) {
            //равномерное распределение лифтов по этажам
            int startFloor = minFloor + (i * (maxFloor - minFloor) / numberOfLifts);
            Lift lift = new Lift(i + 1, maxFloor, startFloor);
            lifts.add(lift);

            Thread liftThread = new Thread(lift, "Лифт-" + (i + 1));
            liftThread.setDaemon(true);
            liftThread.start();
        }

        logger.info("Диспетчер", "Инициализировано лифтов: " + numberOfLifts);
    }

    //добавляет новый запрос в систему
    public void submitRequest(Request request) {
        try {
            requestQueue.put(request);
            totalRequests.incrementAndGet();
            logger.logRequest("Диспетчер", request.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    //назначает запрос наиболее подходящему лифту
    private void assignRequestToLift(Request request) {
        Lift selectedLift = selectBestLift(request);

        if (selectedLift != null) {
            selectedLift.addRequest(request);
            logger.info("Диспетчер", "Запрос " + request + " назначен лифту " + selectedLift.getId());
        } else {
            logger.warning("Диспетчер", "Не найден подходящий лифт для запроса: " + request + ". Повторная попытка...");
            try {
                Thread.sleep(100);
                submitRequest(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    //выбираем лучший лифт для обслуживания запроса
    private Lift selectBestLift(Request request) {
        Lift bestLift = null;
        int bestScore = Integer.MAX_VALUE; //начальное значение это максимально плохой результат

        for (Lift lift : lifts) {
            //пропуск лифтов на обслуживании
            if (lift.getStatus() == com.elevator.model.LiftStatus.MAINTENANCE) {
                continue;
            }

            //расчет оценки пригодности лифта
            int score = calculateScore(lift, request);
            if (score < bestScore && lift.canServeRequest(request)) {
                bestScore = score;
                bestLift = lift;
            }
        }

        return bestLift;
    }

    //рассчитывает оценку пригодности лифта для запроса
    //чем меньше оценка - тем лучше лифт подходит
    private int calculateScore(Lift lift, Request request) {
        int distance = Math.abs(lift.getCurrentFloor() - request.getFloor());
        int directionPenalty = 0;

        //проверка направления движения лифта
        if (lift.getDirection() != Direction.IDLE) {
            boolean isGoodMatch = false;

            //лифт движется вверх и запрос тоже вверх и выше текущего этажа
            if (lift.getDirection() == Direction.UP &&
                    lift.getCurrentFloor() < request.getFloor() &&
                    request.getDirection() == Direction.UP) {
                isGoodMatch = true;
            }
            //лифт движется вниз и запрос тоже вниз и ниже текущего этажа
            else if (lift.getDirection() == Direction.DOWN &&
                    lift.getCurrentFloor() > request.getFloor() &&
                    request.getDirection() == Direction.DOWN) {
                isGoodMatch = true;
            }

            //штраф за движение в другом направлении
            if (!isGoodMatch) {
                directionPenalty = 10;
            }
        }

        //щтраф за загруженность
        int capacityPenalty = lift.getCurrentPassengers() * 2;

        return distance + directionPenalty + capacityPenalty;
    }

    public void emergencyStopAll() {
        logger.logEmergency("КОМАНДА АВАРИЙНОЙ ОСТАНОВКИ!");
        for (Lift lift : lifts) {
            lift.setMaintenance(true);
        }
        running = false;
    }

    public void resumeAll() {
        logger.logEmergency("ВОЗОБНОВЛЕНИЕ РАБОТЫ!");
        for (Lift lift : lifts) {
            lift.setMaintenance(false);
        }
        running = true;
    }

    @Override
    public void run() {
        logger.info("Диспетчер", "Запущен с " + lifts.size() + " лифтами");

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Request request = requestQueue.take();
                assignRequestToLift(request);

                if (totalRequests.get() % 10 == 0) {
                    logStatistics();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.info("Диспетчер", "Остановлен. Всего обработано запросов: " + totalRequests.get());
    }

    private void logStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Всего обработано запросов: ").append(totalRequests.get()).append("\n");
        stats.append("Запросов в очереди: ").append(requestQueue.size()).append("\n");

        //инфа о каждом лифте
        for (Lift lift : lifts) {
            stats.append(String.format("Лифт %d: Этаж %d, %s, Пассажиры: %d%n",
                    lift.getId(),
                    lift.getCurrentFloor(),
                    lift.getDirection(),
                    lift.getCurrentPassengers()));
        }

        logger.logStatistics(stats.toString());
    }

    public List<Lift> getLifts() {
        return new ArrayList<>(lifts);
    }

    public int getQueueSize() {
        return requestQueue.size();
    }
}
