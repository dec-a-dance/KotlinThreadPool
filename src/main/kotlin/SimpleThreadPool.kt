import java.util.Date
import java.util.LinkedList
import kotlin.random.Random

class SimpleThreadPool(
    private val threadsCount: Int = 4
) {

    // в качестве аналога deque будем использовать LinkedList, который будет хранить лямбды для исполнения в потоках
    private val tasks = LinkedList<() -> Unit>()

    // объект который будем использовать для блокирования synchronized блоков
    private val lock = Object()

    // volatile поле для определения шатдауна
    @Volatile
    private var isShutdown = false

    // список базовых JVM-тредов, которые будут выполнять роль потоков в пуле
    private val workers = mutableListOf<Thread>()

    // при инициализации прогоняем создание threadCounts раз треда, которые будут существовать в пуле
    init {
        repeat(threadsCount) { index ->
            val threadNumber = index + 1

            val worker = Thread {
                println("Worker-$threadNumber запущен")

                // вечный цикл работающий по правилу - пока не шатдаун пытаемся получить задачу из листа
                while (true) {
                    var task: (() -> Unit)? = null
                    var exit = false

                    synchronized(lock) {
                        while (tasks.isEmpty() && !isShutdown) {
                            lock.wait()
                        }

                        if (isShutdown && tasks.isEmpty()) {
                            println("Worker-$threadNumber завершает работу")
                            exit = true
                        }

                        task = tasks.poll()
                    }

                    if(exit) break

                    task?.invoke()
                }
            }

            worker.name = "Worker-$threadNumber"
            worker.start()
            workers += worker
        }
    }

    fun post(task: () -> Unit) {
        synchronized(lock) {
            if (isShutdown) {
                throw IllegalStateException("ThreadPool уже остановлен, новые задачи не принимаются")
            }
            tasks.add(task)
            lock.notify()
        }
    }

    fun shutdown(force: Boolean = false) {
        synchronized(lock) {
            isShutdown = true
            lock.notifyAll()
        }

        // возможность закрыть пул с обрыванием потоков (кривовато но сильнее оптимизировать кажется бессмысленным на таком уровне)
        if (force){
            tasks.clear()

            for (worker in workers) {
                worker.interrupt()
            }

            for (worker in workers) {
                try {
                    worker.join()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }

}

fun main() {
    val pool = SimpleThreadPool(4)
    //val currTimeMillis = Date().time

    for (i in 1..10) {
        pool.post {
            val threadName = Thread.currentThread().name
            val threadId = Thread.currentThread().id
            try {
                println("Задача $i выполняется в потоке '$threadName' (id=$threadId)") // логгируем еще id треда чтобы убедиться что не были созданы дубликаты с одинаковыми именами
                Thread.sleep(Random.nextLong(1, 5) * 1000)
            }
            catch(e: InterruptedException){
                println("Выполнение Задачи $i было прервано извне.") // для тестирования shutdown(true)
            }
            //println("Задача $i закончилась через " + (Date().time - currTimeMillis) + " милисекунд после начала исполнения.")
        }
    }

    Thread.sleep(2000)
    pool.shutdown()

    try {
        pool.post {
            // это тестовая задача чтобы проверить бросит ли эксепшен если пул уже закрыт
        }
    }
    catch(e: IllegalStateException){
        println("Было выброшено исключение: " + e.message)
    }

    println("Главный поток завершил работу")
}