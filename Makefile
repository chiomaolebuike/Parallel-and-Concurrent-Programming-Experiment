# PCP1 Assignment
# Compiles and runs the Parallel Dungeon Hunter program

JAVAC = javac
ARGS ?= 100 0.2 20

all:
	$(JAVAC) *.java

run: all
	java DungeonHunterParallel $(ARGS)

clean:
	rm -f *.class
