package com.elevator.core;

import com.elevator.model.Direction;
import com.elevator.model.LiftStatus;
import com.elevator.model.Request;
import com.elevator.util.SimulationLogger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

//класс, представляющий лифт в системе.
//каждый лифт работает в отдельном потоке и управляет своим движением.

public class Lift implements Runnable {
    private final int id;                          //уникальный идентификатор лифта
    private final int maxFloor;
    private final int maxCapacity;
    private int currentFloor;
    private Direction direction;                   //направление движения
    private LiftStatus status;                     //текущее состояние лифта
    private final BlockingQueue<Request> internalRequests; //очередь запросов для этого лифта
    private final ConcurrentSkipListSet<Integer> targetFloors; //множество целевых этажей
    private final ReentrantLock lock;
    private int currentPassengers;
    private int totalRequestsServed;               //счетчик всех обслуженных запросов
    private final SimulationLogger logger;

    private static final int DOOR_OPERATION_TIME = 1000; //время открытия и закрытия дверей (мс)
    private static final int FLOOR_TRAVEL_TIME = 500;    //время движения между этажами (мс)

    public Lift(int id, int maxFloor, int startFloor) {
        this.id = id;
        this.maxFloor = maxFloor;
        this.maxCapacity = 8; // фиксированная вместимость лифта
        this.currentFloor = startFloor;
        this.direction = Direction.IDLE;
        this.status = LiftStatus.STOPPED;
        this.internalRequests = new LinkedBlockingQueue<>();
        this.targetFloors = new ConcurrentSkipListSet<>();
        this.lock = new ReentrantLock();
        this.currentPassengers = 0;
        this.totalRequestsServed = 0;
        this.logger = SimulationLogger.getInstance();
    }

    //добавляет новый запрос в лифт
    public void addRequest(Request request) {
        lock.lock();
        try {
            if (request.isInternal() && request.getTargetFloor() != null) {
                targetFloors.add(request.getTargetFloor());
                logger.logLiftEvent(id, "Добавлен внутренний запрос на этаж " + request.getTargetFloor());
            }
            internalRequests.offer(request); //добавление запроса в очередь
            totalRequestsServed++;
        } finally {
            lock.unlock();
        }
    }

    //проверяет, может ли лифт обслужить данный запрос
    //учитывает направление, текущую загрузку и состояние лифта
    public boolean canServeRequest(Request request) {
        lock.lock();
        try {
            //лифт на обслуживании не может принимать запросы
            if (status == LiftStatus.MAINTENANCE) return false;

            //проверка вместимости
            if (currentPassengers >= maxCapacity) return false;

            //если лифт стоит то может принять любой запрос
            if (direction == Direction.IDLE) return true;

            if (request.getDirection() != direction) return false;

            //проверка возможности подбора пассажира по направлению движения
            if (direction == Direction.UP && request.getFloor() >= currentFloor) return true;
            if (direction == Direction.DOWN && request.getFloor() <= currentFloor) return true;

            return false;
        } finally {
            lock.unlock();
        }
    }

    //обрабатывает остановку лифта на этаже
    private void processStop() {
        logger.logLiftEvent(id, "Остановка на этаже " + currentFloor);
        status = LiftStatus.STOPPED;

        try {
            openDoors();
            Thread.sleep(DOOR_OPERATION_TIME);

            if (Math.random() > 0.7) {
                int passengersOut = (int)(Math.random() * 3);
                currentPassengers = Math.max(0, currentPassengers - passengersOut);
                if (passengersOut > 0) {
                    logger.debug("Лифт-" + id, "Вышли пассажиры: " + passengersOut + ", осталось: " + currentPassengers);
                }
            }

            closeDoors();
            Thread.sleep(DOOR_OPERATION_TIME / 2);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void openDoors() {
        status = LiftStatus.DOORS_OPEN;
        logger.logLiftEvent(id, "Открытие дверей на этаже " + currentFloor);
    }

    private void closeDoors() {
        logger.logLiftEvent(id, "Закрытие дверей на этаже " + currentFloor);
        status = LiftStatus.STOPPED;
    }

    private void move() {
        try {
            //имитация времени движения между этажами
            Thread.sleep(FLOOR_TRAVEL_TIME);

            //движение в зависимости от направления
            if (direction == Direction.UP) {
                currentFloor++;
                //если достигли верхнего этажа то меняем направление
                if (currentFloor >= maxFloor) {
                    direction = Direction.DOWN;
                }
            } else if (direction == Direction.DOWN) {
                currentFloor--;
                //если достигли первого этажа тоже меняем
                if (currentFloor <= 1) {
                    direction = Direction.UP;
                }
            }

            logger.debug("Лифт-" + id, "Движение на этаж " + currentFloor + " (направление: " + direction + ")");

            if (shouldStopAtCurrentFloor()) {
                processStop();
                removeCurrentFloorFromTargets();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    //проверяет, нужно ли остановиться на текущем этаже
    private boolean shouldStopAtCurrentFloor() {
        return targetFloors.contains(currentFloor) ||
                (direction != Direction.IDLE && shouldPickupAtCurrentFloor());
    }

    //проверяет, есть ли на текущем этаже пассажиры для подбора
    private boolean shouldPickupAtCurrentFloor() {
        return internalRequests.stream()
                .anyMatch(req -> !req.isInternal() &&
                        req.getFloor() == currentFloor &&
                        req.getDirection() == direction);
    }

    private void removeCurrentFloorFromTargets() {
        targetFloors.remove(currentFloor);
        internalRequests.removeIf(req -> !req.isInternal() &&
                req.getFloor() == currentFloor &&
                req.getDirection() == direction);
    }

    //определяет направление движения лифта на основе текущих целей
    //если целей нет - лифт переходит в режим покоя

    private void determineDirection() {
        if (targetFloors.isEmpty() && internalRequests.isEmpty()) {
            direction = Direction.IDLE;
            return;
        }

        //если лифт стоит то выбираем ближайшую цель
        if (direction == Direction.IDLE) {
            Integer nextTarget = getNearestTarget();
            if (nextTarget != null) {
                direction = (nextTarget > currentFloor) ? Direction.UP : Direction.DOWN;
            }
        }
    }

    private Integer getNearestTarget() {
        if (targetFloors.isEmpty()) return null;

        //поиск ближайшего этажа выше и ниже текущего
        Integer higher = targetFloors.higher(currentFloor);
        Integer lower = targetFloors.lower(currentFloor);

        if (higher == null) return lower;
        if (lower == null) return higher;

        int distToHigher = higher - currentFloor;
        int distToLower = currentFloor - lower;

        return (distToHigher <= distToLower) ? higher : lower;
    }

    @Override
    public void run() {
        logger.logLiftEvent(id, "Запущен. Начальный этаж: " + currentFloor);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                determineDirection();

                if (direction == Direction.IDLE) {
                    Thread.sleep(100);
                    continue;
                }

                status = LiftStatus.MOVING;
                move();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        logger.logLiftEvent(id, "Остановлен. Всего обслужено запросов: " + totalRequestsServed);
    }

    public int getCurrentFloor() {
        lock.lock();
        try {
            return currentFloor;
        } finally {
            lock.unlock();
        }
    }

    public Direction getDirection() {
        lock.lock();
        try {
            return direction;
        } finally {
            lock.unlock();
        }
    }

    public LiftStatus getStatus() {
        lock.lock();
        try {
            return status;
        } finally {
            lock.unlock();
        }
    }

    public int getId() {
        return id;
    }

    public int getCurrentPassengers() {
        lock.lock();
        try {
            return currentPassengers;
        } finally {
            lock.unlock();
        }
    }

    public void setMaintenance(boolean maintenance) {
        lock.lock();
        try {
            status = maintenance ? LiftStatus.MAINTENANCE : LiftStatus.STOPPED;
            direction = Direction.IDLE;
            logger.logLiftEvent(id, maintenance ? "Переведен в режим обслуживания" : "Выход из режима обслуживания");
        } finally {
            lock.unlock();
        }
    }
}