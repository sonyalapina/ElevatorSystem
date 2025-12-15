# ElevatorSystem
 
Для запуска: cd "C:\Users\1\Desktop\ElevatorSystem"; Remove-Item "out" -Recurse -Force -ErrorAction SilentlyContinue; mkdir out; javac -d out src/com/elevator/model/*.java; javac -d out -cp out src/com/elevator/util/SimulationLogger.java; javac -d out -cp out src/com/elevator/core/Lift.java; javac -d out -cp out src/com/elevator/core/Dispatcher.java; javac -d out -cp out src/com/elevator/util/BuildingSimulation.java; java -cp out com.elevator.util.BuildingSimulation
