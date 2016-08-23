/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.tasks;

import android.app.Activity;
import android.support.annotation.NonNull;

import com.example.android.architecture.blueprints.todoapp.addedittask.AddEditTaskActivity;
import com.example.android.architecture.blueprints.todoapp.data.Task;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksDataSource;
import com.example.android.architecture.blueprints.todoapp.data.source.TasksRepository;
import com.example.android.architecture.blueprints.todoapp.util.EspressoIdlingResource;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Listens to user actions from the UI ({@link TasksFragment}), retrieves the data and updates the
 * UI as required.
 */
public class TasksPresenter implements TasksContract.Presenter {
    /**
     * 用于存储数据源和获取数据源
     */
    private final TasksRepository mTasksRepository;
    /**
     * 用于控制ui状态
     */
    private final TasksContract.View mTasksView;

    private TasksFilterType mCurrentFiltering = TasksFilterType.ALL_TASKS;

    private boolean mFirstLoad = true;

    public TasksPresenter(@NonNull TasksRepository tasksRepository, @NonNull TasksContract.View tasksView) {
        mTasksRepository = checkNotNull(tasksRepository, "tasksRepository cannot be null");
        mTasksView = checkNotNull(tasksView, "tasksView cannot be null!");
        //使 view 持有 Presenter
        mTasksView.setPresenter(this);
    }

    @Override
    public void start() {
        //第一次开始加载数据
        loadTasks(false);
    }

    @Override
    public void result(int requestCode, int resultCode) {
        // If a task was successfully added, show snackbar
        if (AddEditTaskActivity.REQUEST_ADD_TASK == requestCode && Activity.RESULT_OK == resultCode) {
            mTasksView.showSuccessfullySavedMessage();
        }
    }

    @Override
    public void loadTasks(boolean forceUpdate) {
        // Simplification for sample: a network reload will be forced on first load.
        //显示 loading,第一次加载就刷新数据
        loadTasks(forceUpdate || mFirstLoad, true);
        mFirstLoad = false;
    }

    /**
     * 加载数据
     * @param forceUpdate   Pass in true to refresh the data in the {@link TasksDataSource}
     * @param showLoadingUI Pass in true to display a loading icon in the UI
     */
    private void loadTasks(boolean forceUpdate, final boolean showLoadingUI) {
        //控制 UI 是否显示 loading
        if (showLoadingUI) {
            mTasksView.setLoadingIndicator(true);
        }
        //控制数据源是否刷新
        if (forceUpdate) {
            mTasksRepository.refreshTasks();
        }

        // The network request might be handled in a different thread so make sure Espresso knows
        // that the app is busy until the response is handled.
        EspressoIdlingResource.increment(); // App is busy until further notice
        //从数据源获取数据
        mTasksRepository.getTasks(new TasksDataSource.LoadTasksCallback() {
            @Override
            public void onTasksLoaded(List<Task> tasks) {
                //从数据源获取到数据
                List<Task> tasksToShow = new ArrayList<Task>();

                // This callback may be called twice, once for the cache and once for loading
                // the data from the server API, so we check before decrementing, otherwise
                // it throws "Counter has been corrupted!" exception.
                if (!EspressoIdlingResource.getIdlingResource().isIdleNow()) {
                    EspressoIdlingResource.decrement(); // Set app as idle.
                }

                // We filter the tasks based on the requestType
                //根据不同类型对数据源进行过滤，添加到列表中
                for (Task task : tasks) {
                    switch (mCurrentFiltering) {
                        case ALL_TASKS:
                            tasksToShow.add(task);
                            break;
                        case ACTIVE_TASKS:
                            if (task.isActive()) {
                                tasksToShow.add(task);
                            }
                            break;
                        case COMPLETED_TASKS:
                            if (task.isCompleted()) {
                                tasksToShow.add(task);
                            }
                            break;
                        default:
                            tasksToShow.add(task);
                            break;
                    }
                }
                // The view may not be able to handle UI updates anymore
                //判断 如果ui状态不可用结束方法
                if (!mTasksView.isActive()) {
                    return;
                }
                if (showLoadingUI) {
                    //将 loading 取消掉
                    mTasksView.setLoadingIndicator(false);
                }
            //处理获取到的数据结果进行显示
                processTasks(tasksToShow);
            }

            @Override
            public void onDataNotAvailable() {
                // The view may not be able to handle UI updates anymore
                if (!mTasksView.isActive()) {
                    return;
                }
                mTasksView.showLoadingTasksError();
            }
        });
    }

    private void processTasks(List<Task> tasks) {
        if (tasks.isEmpty()) {
            //如果数据源为空处理方式
            // Show a message indicating there are no tasks for that filter type.
            processEmptyTasks();
        } else {
            //数据不为空，进行显示数据处理ui
            // Show the list of tasks
            mTasksView.showTasks(tasks);
            // Set the filter label's text.
            //设置过滤标签文字
            showFilterLabel();
        }
    }

    private void showFilterLabel() {
        switch (mCurrentFiltering) {
            case ACTIVE_TASKS:
                mTasksView.showActiveFilterLabel();
                break;
            case COMPLETED_TASKS:
                mTasksView.showCompletedFilterLabel();
                break;
            default:
                mTasksView.showAllFilterLabel();
                break;
        }
    }

    /**
     * 根据不同状态处理数据为空的情况，进行显示 ui
     */
    private void processEmptyTasks() {
        switch (mCurrentFiltering) {
            case ACTIVE_TASKS:
                mTasksView.showNoActiveTasks();
                break;
            case COMPLETED_TASKS:
                mTasksView.showNoCompletedTasks();
                break;
            default:
                mTasksView.showNoTasks();
                break;
        }
    }

    /**
     * 添加新的数据
     */
    @Override
    public void addNewTask() {
        //根据添加新的数据处理显示ui
        mTasksView.showAddTask();
    }

    /**
     * 显示数据详情
     * @param requestedTask
     */
    @Override
    public void openTaskDetails(@NonNull Task requestedTask) {
        checkNotNull(requestedTask, "requestedTask cannot be null!");
        //处理ui显示数据详情
        mTasksView.showTaskDetailsUi(requestedTask.getId());
    }
    //编辑完成
    @Override
    public void completeTask(@NonNull Task completedTask) {
        checkNotNull(completedTask, "completedTask cannot be null!");
        //处理数据层
        mTasksRepository.completeTask(completedTask);
        //处理ui层
        mTasksView.showTaskMarkedComplete();
        //处理数据层
        loadTasks(false, false);
    }

    /**
     * // 标记未完成 处于编辑状态
     * @param activeTask
     */
    @Override
    public void activateTask(@NonNull Task activeTask) {
        checkNotNull(activeTask, "activeTask cannot be null!");
        //处理数据
        mTasksRepository.activateTask(activeTask);
        //处理ui
        mTasksView.showTaskMarkedActive();
        loadTasks(false, false);
    }

    /**
     * 清空 Task 控制
     */
    @Override
    public void clearCompletedTasks() {
        //数据层处理
        mTasksRepository.clearCompletedTasks();
        //view层处理
        mTasksView.showCompletedTasksCleared();
        loadTasks(false, false);
    }

    /**
     * 设置过滤类型
     * Sets the current task filtering type.
     *
     * @param requestType Can be {@link TasksFilterType#ALL_TASKS},
     *                    {@link TasksFilterType#COMPLETED_TASKS}, or
     *                    {@link TasksFilterType#ACTIVE_TASKS}
     */
    @Override
    public void setFiltering(TasksFilterType requestType) {
        mCurrentFiltering = requestType;
    }

    /**
     * 获取过滤类型
     * @return
     */
    @Override
    public TasksFilterType getFiltering() {
        return mCurrentFiltering;
    }

}
