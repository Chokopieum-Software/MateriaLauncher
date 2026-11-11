/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.net

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import java.util.*

/**
 * Представляет одну задачу загрузки.
 */
data class DownloadTask(
    val id: String,
    val description: String,
    val job: Job,
    val progress: androidx.compose.runtime.State<Float> = mutableStateOf(0f),
    val status: androidx.compose.runtime.State<String> = mutableStateOf("В очереди..."),
    val transferMeter: TransferMeter = TransferMeter(),
    val transferStats: androidx.compose.runtime.MutableState<TransferStats> = mutableStateOf(TransferStats())
)

/**
 * Глобальный менеджер для отслеживания всех активных загрузок в приложении.
 * Это синглтон (object), чтобы к нему можно было получить доступ из любого места.
 */
object DownloadManager {
    private val scope = CoroutineScope(Dispatchers.Main)

    /**
     * Список активных задач, который может наблюдаться Compose.
     */
    val tasks = mutableStateListOf<DownloadTask>()

    /**
     * Начинает новую задачу и добавляет ее в список отслеживания.
     * @return Созданная задача.
     */
    fun startTask(description: String, job: Job): DownloadTask {
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            description = description,
            job = job
        )
        scope.launch {
            tasks.add(task)
            while (task in tasks && task.job.isActive) {
                task.transferStats.value = task.transferMeter.snapshot()
                delay(1000)
            }
        }
        return task
    }

    /**
     * Обновляет прогресс и статус существующей задачи.
     */
    fun updateTask(id: String, newProgress: Float, newStatus: String) {
        scope.launch {
            tasks.find { it.id == id }?.let { task ->
                (task.progress as androidx.compose.runtime.MutableState).value = newProgress
                (task.status as androidx.compose.runtime.MutableState).value = newStatus
            }
        }
    }

    /**
     * Отменяет задачу и удаляет ее из списка.
     */
    fun cancelTask(id: String) {
        scope.launch {
            tasks.find { it.id == id }?.let {
                it.job.cancelAndJoin()
                tasks.remove(it)
            }
        }
    }

    /**
     * Удаляет задачу из списка.
     */
    fun removeTask(id: String) {
        scope.launch {
            tasks.removeIf { it.id == id }
        }
    }
}
