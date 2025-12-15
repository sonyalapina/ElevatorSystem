package com.elevator.util;

import com.elevator.core.Dispatcher;
import com.elevator.model.Direction;
import com.elevator.model.Request;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//класс для симуляции работы системы лифтов в многоквартирном доме
public class BuildingSimulation {
    private final Dispatcher dispatcher;         //диспетчер системы лифтов
    private final int maxFloor;
    private final Random random;                 //генератор случайных чисел
    private final SimulationLogger logger;
    private ScheduledExecutorService scheduler;  //планировщик задач для генерации событий
    private volatile boolean running;            //флаг работы симуляции

    public BuildingSimulation(int numberOfLifts, int maxFloor) {
        this.maxFloor = maxFloor;
        this.dispatcher = new Dispatcher(numberOfLifts, maxFloor);
        this.random = new Random();
        this.logger = SimulationLogger.getInstance();
        this.running = false;
    }

    public void start() {
        if (running) return;
        running = true;
        logger.start();

        logger.info("Симуляция", "Запуск симуляции лифтов");
        logger.info("Симуляция", "Этажи: 1-" + maxFloor);
        logger.info("Симуляция", "Количество лифтов: " + dispatcher.getLifts().size());

        Thread dispatcherThread = new Thread(dispatcher, "Поток-диспетчера");
        dispatcherThread.setDaemon(true); //демон-поток
        dispatcherThread.start();

        //создание планировщика задач
        scheduler = Executors.newScheduledThreadPool(2);

        //планирование задач:

        //генерация внешних запросов
        scheduler.scheduleAtFixedRate(this::generateExternalRequest, 0, 2, TimeUnit.SECONDS);

        //генерация внутренних
        scheduler.scheduleAtFixedRate(this::generateInternalRequest, 1, 3, TimeUnit.SECONDS);

        //генерация внештатных ситуаций
        scheduler.scheduleAtFixedRate(this::generateEmergency, 30, 60, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(this::printStatistics, 15, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;

        logger.info("Симуляция", "Остановка симуляции");

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        //аварийная остановка всех лифтов
        dispatcher.emergencyStopAll();
        logger.stop();
    }

    private void generateExternalRequest() {
        if (!running) return;

        //случайный выбор этажа
        int floor = random.nextInt(maxFloor) + 1;
        Direction direction;

        //определение направления для крайних этажей
        if (floor == 1) {
            direction = Direction.UP; //с первого этажа только вверх
        } else if (floor == maxFloor) {
            direction = Direction.DOWN; //с последнего вниз
        } else {
            //для остальных случайное направление
            direction = random.nextBoolean() ? Direction.UP : Direction.DOWN;
        }

        Request request = new Request(floor, direction);
        dispatcher.submitRequest(request);
    }

    private void generateInternalRequest() {
        if (!running) return;
        var lifts = dispatcher.getLifts();
        if (lifts.isEmpty()) return;

        //случайный выбор лифта
        var lift = lifts.get(random.nextInt(lifts.size()));

        int currentFloor = lift.getCurrentFloor();
        int targetFloor;

        //выбор целевого этажа
        do {
            targetFloor = random.nextInt(maxFloor) + 1;
        } while (targetFloor == currentFloor);

        //определение направления
        Direction direction = (targetFloor > currentFloor) ? Direction.UP : Direction.DOWN;
        Request request = new Request(currentFloor, direction, targetFloor);
        dispatcher.submitRequest(request);
    }

    private void generateEmergency() {
        if (!running || random.nextInt(100) > 20) return;

        var lifts = dispatcher.getLifts();
        if (lifts.isEmpty()) return;

        //случайный выбор лифта для внештатной ситуации
        var lift = lifts.get(random.nextInt(lifts.size()));

        if (random.nextBoolean()) {
            //техническое обслуживание лифта
            logger.logEmergency("Лифт " + lift.getId() + " требует технического обслуживания");
            lift.setMaintenance(true);

            //автоматическое восстановление через 10 секунд
            scheduler.schedule(() -> {
                logger.logEmergency("Лифт " + lift.getId() + " завершил обслуживание");
                lift.setMaintenance(false);
            }, 10, TimeUnit.SECONDS);
        } else {
            //пожарная тревога
            logger.logEmergency("Пожарная тревога! Все лифты направляются на первый этаж");
            dispatcher.emergencyStopAll();

            //отмена тревоги через 15 секунд
            scheduler.schedule(() -> {
                logger.logEmergency("Пожарная тревога отменена. Возобновление работы");
                dispatcher.resumeAll();
            }, 15, TimeUnit.SECONDS);
        }
    }

    private void printStatistics() {
        if (!running) return;

        StringBuilder stats = new StringBuilder();
        stats.append("Текущая статистика системы:\n");
        stats.append("Запросов в очереди диспетчера: ").append(dispatcher.getQueueSize()).append("\n");

        var lifts = dispatcher.getLifts();
        for (var lift : lifts) {
            stats.append(String.format("Лифт %d: Этаж %d, %s, %s, Пассажиры: %d%n",
                    lift.getId(),
                    lift.getCurrentFloor(),
                    lift.getDirection(),
                    lift.getStatus(),
                    lift.getCurrentPassengers()));
        }

        logger.logStatistics(stats.toString());
    }

    public void manualRequest(int floor, Direction direction) {
        Request request = new Request(floor, direction);
        dispatcher.submitRequest(request);
        logger.info("Ручное управление", "Вызов лифта: этаж " + floor + ", направление " + direction);
    }

    public void manualRequest(int fromFloor, int toFloor) {
        Direction direction = (toFloor > fromFloor) ? Direction.UP : Direction.DOWN;
        Request request = new Request(fromFloor, direction, toFloor);
        dispatcher.submitRequest(request);
        logger.info("Ручное управление", "Запрос внутри лифта: " + fromFloor + " → " + toFloor);
    }

    public static void main(String[] args) {
        //создание симуляции с 4 лифтами в 20этажном здании
        BuildingSimulation simulation = new BuildingSimulation(4, 20);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Завершение работы по сигналу выключения...");
            simulation.stop();
        }));

        try {
            simulation.start();
            Thread.sleep(30000);
            simulation.manualRequest(1, Direction.UP);
            Thread.sleep(2000);
            simulation.manualRequest(5, 10);
            Thread.sleep(2000);
            Thread.sleep(30000);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Симуляция прервана: " + e.getMessage());
        } finally {
            simulation.stop();
        }
    }
}