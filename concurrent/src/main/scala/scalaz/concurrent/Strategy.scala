package scalaz
package concurrent

import java.util.concurrent._

/**
 * Evaluate an expression in some specific manner. A typical strategy will schedule asynchronous
 * evaluation and return a function that, when called, will block until the result is ready.
 *
 * Memory consistency effects: Actions in a thread prior to the submission of `a`
 * to the `Strategy` happen-before any actions taken by `a`, which in turn happen-before
 * the result is retrieved via returned function.
 */
trait Strategy {
  def apply[A](a: => A): () => A
}

object Strategy extends Strategys

trait Strategys extends StrategysLow {

  /**
   * Default thread factory to mark all threads as daemon
   */
  lazy val DefaultDaemonThreadFactory = new ThreadFactory {
    val defaultThreadFactory = Executors.defaultThreadFactory()

    def newThread(r: Runnable) = {
      val t = defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t
    }
  }

  /**
   * The default executor service is a fixed thread pool with N daemon threads,
   * where N is equal to the number of available processors.
   */
  lazy val DefaultExecutorService: ExecutorService =
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors, DefaultDaemonThreadFactory)

  /**
   * Default scheduler used for scheduling the tasks like timeout.
   */
  lazy val DefaultTimeoutScheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1,
    DefaultDaemonThreadFactory)

  /**
   * A strategy that executes its arguments on `DefaultExecutorService`
   */
  implicit lazy val DefaultStrategy: Strategy = Executor(DefaultExecutorService)
}

trait StrategysLow {

  /**
   * A strategy that evaluates its argument in the current thread.
   */
  implicit val Sequential: Strategy = new Strategy {
    def apply[A](a: => A) = {
      val v = a
      () => v
    }
  }

  /**
   * A strategy that evaluates its arguments using an implicit ExecutorService.
   */
  implicit def Executor(implicit s: ExecutorService) = new Strategy {
    def apply[A](a: => A) = {
      val fut = s.submit(new Callable[A] {
        def call = a
      })
      () => fut.get
    }
  }

  /**
   * A strategy that performs no evaluation of its argument.
   */
  implicit val Id: Strategy = new Strategy {
    def apply[A](a: => A) = () => a
  }

  /**
   * A simple strategy that spawns a new thread for every evaluation.
   */
  implicit val Naive: Strategy = new Strategy {
    def apply[A](a: => A) = {
      val executorService = Executors.newSingleThreadExecutor(Strategy.DefaultDaemonThreadFactory)
      val future = executorService.submit(new Callable[A] {
        def call = a
      })
      executorService.shutdown()
      () => future.get
    }
  }

  /**
   * A strategy that evaluates its arguments using the pool of Swing worker threads.
   */
  implicit val SwingWorker: Strategy = new Strategy {

    import javax.swing.SwingWorker

    def apply[A](a: => A) = {
      val worker = new SwingWorker[A, Unit] {
        def doInBackground() = a
      }
      worker.execute()
      () => worker.get
    }
  }

  /**
   * A strategy that evaluates its arguments on the Swing Event Dispatching thread.
   */
  implicit val SwingInvokeLater: Strategy = new Strategy {

    import javax.swing.SwingUtilities.invokeLater

    def apply[A](a: => A) = {
      val task = new FutureTask[A](new Callable[A] {
        def call = a
      })
      invokeLater(task)
      () => task.get
    }
  }
}
