/*
 * Copyright (C) 2015-2016 Dionysis Lappas <dio@freelabs.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.freelabs.maestro.broker.tasks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * Class that defines an executor for Tasks.
 */
public final class TaskExecutor {
    /**
     * List of tasks to execute.
     */
    private final List<Task> tasks;
    /**
     * Constructor.
     * @param tasks tasks to execute.
     */
    public TaskExecutor(Task... tasks){
        this.tasks = Arrays.asList(tasks);
    }
    /**
     * Constructor.
     */
    public TaskExecutor(){
        this.tasks = new ArrayList<>();
    }
    /**
     * Adds a task to the task list.
     * @param task a task to execute.
     */
    public void addTask(Task task){
            tasks.add(task);
    }
    /**
     * Executes defines tasks.
     */
    public void execTasks(){
        tasks.stream().forEach((task) -> {
            task.run();
        });
    }
    
}
