package concurrency;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Semaphore;

/**
 * 多线程交替打印ABC的多种实现方式
 * @author WU
 */
public class SynPrintABC {
    /**
     * 给第三种方法(StateLock)用的状态变量
     */
    static volatile int state = 0;

    /**
     * 给第四种方法用的信号量
     * acquire()会获取许可并且把permit减1，为0就不能用
     * release()会释放许可，permit许可数+1
     */
    // 初始化A信号量为1，可以先运行
    static Semaphore semaphoreA = new Semaphore(1);
    // 初始化B信号量为0，先阻塞
    static Semaphore semaphoreB = new Semaphore(0);
    // 初始化C信号量为0，先阻塞
    static Semaphore semaphoreC = new Semaphore(0);

    /**
     * 用两个lock + synchronized多线程交替打印ABC
     * @author WU
     */
    static class TwoLockPrint implements Runnable {
        /**
         * 打印次数
         */
        private static final int PRINT_COUNT = 10;

        /**
         * 前一个线程的打印锁
         */
        private final Object prevLock;

        /**
         * 本线程的打印锁
         */
        private final Object selfLock;

        /**
         * 打印的字符
         */
        private final char printChar;

        /**
         * constructor
         *
         * @param prevLock
         * @param selfLock
         * @param printChar
         */
        public TwoLockPrint(Object prevLock, Object selfLock, char printChar) {
            this.prevLock = prevLock;
            this.selfLock = selfLock;
            this.printChar = printChar;
        }

        /**
         * 重写Runnable的run方法
         * 等待两把锁进行打印
         */
        @Override
        public void run() {
            for (int i = 0; i < PRINT_COUNT; i++) {
                // 获取前一个线程的锁
                synchronized (prevLock) {
                    // 获取自己的锁
                    synchronized (selfLock) {
                        // 打印
                        System.out.print(printChar);
                        // 打印之后释放自己锁来唤醒下一个打印的线程
                        // notifyAll 和 notify都可以，同一时刻只有一个线程在等待
                        selfLock.notifyAll();
                    }
                    // 通过prevLock等待被唤醒
                    // 最后一次打印之前，在打印完10次之后还会等待唤醒，死锁
                    try {
                        if (i < PRINT_COUNT - 1) {
                            prevLock.wait();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 用可重入锁ReentrantLock和condition实现
     * 和synchronized + 两把锁是同一个思路
     * @author WU
     */
    static class ReentrantCondition implements Runnable{
        /**
         * 打印次数
         */
        private static final int PRINT_COUNT = 10;

        /**
         * 可重入锁
         */
        private final ReentrantLock reentrantLock;

        /**
         * 本线程的condition
         */
        private final Condition selfCondition;

        /**
         * 下一个线程的condition
         */
        private final Condition nextCondition;

        /**
         * 打印的字符
         */
        private final char printChar;

        ReentrantCondition(ReentrantLock reentrantLock, Condition selfCondition, Condition nextCondition, char printChar) {
            this.reentrantLock = reentrantLock;
            this.selfCondition = selfCondition;
            this.nextCondition = nextCondition;
            this.printChar = printChar;
        }

        /**
         * 重写的run方法
         */
        @Override
        public void run() {
            // 获取锁，进入临界区
            reentrantLock.lock();
            try {
                // 打印PRINT_COUNT次
                for(int i = 0; i < PRINT_COUNT; i++){
                    System.out.print(printChar);
                    // 用condition唤醒下一个线程
                    // signal 和 signalAll都可以
                    nextCondition.signalAll();
                    // 不是最后一次打印就通过selfCondition来等待唤醒
                    if(i < PRINT_COUNT - 1){
                        // 让出锁且等待唤醒
                        selfCondition.await();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                // finally释放锁
                reentrantLock.unlock();
            }
        }
    }

    /**
     * synchronized + state变量的方式
     * @author WU
     */
    static class StateLock implements Runnable{
        //打印次数
        private static final int PRINT_COUNT=10;
        //打印锁
        private final Object printLock;
        //打印标志位 和state变量相关
        private final int printFlag;
        //后继线程的线程的打印标志位，state变量相关
        private final int nextPrintFlag;
        //该线程的打印字符
        private final char printChar;

        StateLock(Object printLock, int printFlag, int nextPrintFlag, char printChar) {
            this.printLock = printLock;
            this.printFlag = printFlag;
            this.nextPrintFlag = nextPrintFlag;
            this.printChar = printChar;
        }

        /**
         * 重写run方法
         */
        @Override
        public void run() {
            // 进入临界区
            synchronized (printLock){
                for(int i = 0; i < PRINT_COUNT; i++){
                    // 循环检查state，阻塞等待被唤醒
                    while (state != printFlag){
                        try{
                            printLock.wait();
                        } catch (InterruptedException e){
                            e.printStackTrace();
                        }
                    }
                    // while结束，即state说明可以打印了
                    System.out.print(printChar);
                    // 将state设置成下一个要打印的状态
                    state = nextPrintFlag;
                    // 释放lock
                    // 这里要同notifyAll，另外两个打印线程等同的是一个
                    printLock.notifyAll();
                }
            }
        }
    }

    /**
     * 使用信号量Semaphore的实现
     * @author Wu
     */
    static class SemaphorePrint implements Runnable {
        private static final int PRINT_COUNT = 10;
        /**
         * 自己的信号量
         */
        private final Semaphore thisSemaphore;

        /**
         * 下一个线程的信号量
         */
        private final Semaphore nextSemaphore;

        private final char printChar;

        SemaphorePrint(Semaphore thisSemaphore, Semaphore nextSemaphore, char printChar) {
            this.thisSemaphore = thisSemaphore;
            this.nextSemaphore = nextSemaphore;
            this.printChar = printChar;
        }

        @Override
        public void run() {
            for (int i = 0; i < PRINT_COUNT; i++){
                try {
                    // 获取本线程信号量的许可
                    thisSemaphore.acquire();
                    System.out.print(printChar);
                    // 释放下一个线程的信号量的许可
                    nextSemaphore.release();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        /*// 第一种方法的调度
        // 打印A、B、C的锁
        Object lockA = new Object();
        Object lockB = new Object();
        Object lockC = new Object();

        // 创建三个线程
        // A的前驱是C
        Thread printA = new Thread(new TwoLockPrint(lockC, lockA, 'A'));
        Thread printB = new Thread(new TwoLockPrint(lockA, lockB, 'B'));
        Thread printC = new Thread(new TwoLockPrint(lockB, lockC, 'C'));

        // 依次启动
        // 这种写法依赖于 JVM 的顺序控制，所以需要Thread.sleep手动控制启动顺序
        printA.start();
        Thread.sleep(10);
        printB.start();
        Thread.sleep(10);
        printC.start();*/

        /*// 第二种方法的调用
        // 一把锁
        ReentrantLock lock = new ReentrantLock();
        // 线程A.B.C的条件
        Condition conditionA = lock.newCondition();
        Condition conditionB = lock.newCondition();
        Condition conditionC = lock.newCondition();

        Thread threadA = new Thread(new ReentrantCondition(lock, conditionA, conditionB, 'A'));
        Thread threadB = new Thread(new ReentrantCondition(lock, conditionB, conditionC, 'B'));
        Thread threadC = new Thread(new ReentrantCondition(lock, conditionC, conditionA, 'C'));

        // 也是需要依次开始
        threadA.start();
        Thread.sleep(10);
        threadB.start();
        Thread.sleep(10);
        threadC.start();*/

        /*// 第三种方法的调用
        Object lock = new Object();
        Thread threadA = new Thread(new StateLock(lock, 0, 1, 'A'));
        Thread threadB = new Thread(new StateLock(lock, 1, 2, 'B'));
        Thread threadC = new Thread(new StateLock(lock, 2, 0, 'C'));
        // 依次启动线程
        threadA.start();
        Thread.sleep(10);
        threadB.start();
        Thread.sleep(10);
        threadC.start();*/

        // 第4中方法的调用
        Thread threadA = new Thread(new SemaphorePrint(semaphoreA, semaphoreB, 'A'));
        Thread threadB = new Thread(new SemaphorePrint(semaphoreB, semaphoreC, 'B'));
        Thread threadC = new Thread(new SemaphorePrint(semaphoreC, semaphoreA, 'C'));
        // 启动
        threadA.start();
        threadB.start();
        threadC.start();
    }
}
// site: https://segmentfault.com/a/1190000021433079
//       https://www.cnblogs.com/xiaoxi/p/8035725.html
//       https://blog.csdn.net/xiaokang123456kao/article/details/77331878
