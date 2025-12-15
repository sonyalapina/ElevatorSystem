package com.elevator.model;

//запрос на обслуживание лифтом
//может быть внешним (вызов с этажа) или внутренним (выбор этажа в лифте)

public class Request {
    private final int floor;           //этаж, с которого сделан вызов
    private final Direction direction; //направление
    private final Integer targetFloor; //на какой едем
    private final long timestamp;
    private final boolean isInternal;  //флаг внутреннего запроса

    //конструктор для внешнего запроса
    public Request(int floor, Direction direction) {
        this.floor = floor;
        this.direction = direction;
        this.targetFloor = null;
        this.timestamp = System.currentTimeMillis();
        this.isInternal = false;
    }

    //конструктор для внутреннего
    public Request(int floor, Direction direction, int targetFloor) {
        this.floor = floor;
        this.direction = direction;
        this.targetFloor = targetFloor;
        this.timestamp = System.currentTimeMillis();
        this.isInternal = true;
    }

    public int getFloor() {
        return floor;
    }

    public Direction getDirection() {
        return direction;
    }

    public Integer getTargetFloor() {
        return targetFloor;
    }

    public boolean isInternal() {
        return isInternal;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        if (isInternal) {
            return "Запрос {с этажа " + floor + " на этаж " + targetFloor + ", направление =" + direction + "}";
        }
        return "Запрос {этаж =" + floor + ", направление =" + direction + "}";
    }
}