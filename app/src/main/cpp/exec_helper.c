/*
 * Fork+exec helper: keep a UsbManager usbfs FD open for TomKing spd_dump --usb-fd.
 * Android ProcessBuilder closes FDs > 2; this path clears FD_CLOEXEC on usbFd and
 * only closes other FDs in the child before exec.
 */
#include <jni.h>
#include <android/log.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

#define TAG "spd_host_exec"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static int clear_cloexec(int fd) {
    int flags = fcntl(fd, F_GETFD);
    if (flags < 0) return -1;
    if (flags & FD_CLOEXEC) {
        if (fcntl(fd, F_SETFD, flags & ~FD_CLOEXEC) < 0) return -1;
    }
    return 0;
}

static void close_extra_fds(int keep0, int keep1, int keep2, int keep_usb) {
    int maxfd = (int)sysconf(_SC_OPEN_MAX);
    if (maxfd < 0 || maxfd > 1024) maxfd = 1024;
    for (int fd = 0; fd < maxfd; fd++) {
        if (fd == keep0 || fd == keep1 || fd == keep2 || fd == keep_usb)
            continue;
        close(fd);
    }
}

static void emit_line(JNIEnv *env, jobject cb, jmethodID mid, const char *buf, int n) {
    if (n <= 0) return;
    jstring js = (*env)->NewStringUTF(env, buf);
    if (!js) return;
    (*env)->CallVoidMethod(env, cb, mid, js);
    (*env)->DeleteLocalRef(env, js);
}

JNIEXPORT jint JNICALL
Java_com_seuj09_spddumphost_NativeExec_runWithUsbFd(
        JNIEnv *env, jclass clazz,
        jobjectArray argvJava, jint usbFd, jobject onLine) {
    (void)clazz;
    if (usbFd < 0) {
        LOGE("usbFd < 0");
        return -1;
    }
    if (clear_cloexec(usbFd) != 0) {
        LOGE("clear_cloexec(%d): %s", usbFd, strerror(errno));
        return -1;
    }

    jsize argc = (*env)->GetArrayLength(env, argvJava);
    if (argc < 1) return -1;

    char **argv = calloc((size_t)argc + 1, sizeof(char *));
    if (!argv) return -1;
    for (jsize i = 0; i < argc; i++) {
        jstring js = (jstring)(*env)->GetObjectArrayElement(env, argvJava, i);
        const char *utf = (*env)->GetStringUTFChars(env, js, NULL);
        argv[i] = strdup(utf ? utf : "");
        (*env)->ReleaseStringUTFChars(env, js, utf);
        (*env)->DeleteLocalRef(env, js);
    }

    jclass cbClass = (*env)->GetObjectClass(env, onLine);
    jmethodID onLineMid = (*env)->GetMethodID(env, cbClass, "onLine", "(Ljava/lang/String;)V");
    (*env)->DeleteLocalRef(env, cbClass);
    if (!onLineMid) {
        for (jsize i = 0; i < argc; i++) free(argv[i]);
        free(argv);
        return -1;
    }

    int pipefd[2];
    if (pipe(pipefd) != 0) {
        LOGE("pipe: %s", strerror(errno));
        for (jsize i = 0; i < argc; i++) free(argv[i]);
        free(argv);
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork: %s", strerror(errno));
        close(pipefd[0]);
        close(pipefd[1]);
        for (jsize i = 0; i < argc; i++) free(argv[i]);
        free(argv);
        return -1;
    }

    if (pid == 0) {
        /* child */
        close(pipefd[0]);
        dup2(pipefd[1], STDOUT_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        if (pipefd[1] != STDOUT_FILENO && pipefd[1] != STDERR_FILENO)
            close(pipefd[1]);
        clear_cloexec(usbFd);
        close_extra_fds(STDIN_FILENO, STDOUT_FILENO, STDERR_FILENO, usbFd);
        execv(argv[0], argv);
        dprintf(STDERR_FILENO, "execv(%s): %s\n", argv[0], strerror(errno));
        _exit(127);
    }

    /* parent */
    close(pipefd[1]);
    for (jsize i = 0; i < argc; i++) free(argv[i]);
    free(argv);

    char linebuf[512];
    size_t linelen = 0;
    char readbuf[256];
    for (;;) {
        struct pollfd pfd = { .fd = pipefd[0], .events = POLLIN };
        int pr = poll(&pfd, 1, 250);
        if (pr > 0 && (pfd.revents & POLLIN)) {
            ssize_t n = read(pipefd[0], readbuf, sizeof(readbuf));
            if (n <= 0) break;
            for (ssize_t i = 0; i < n; i++) {
                char c = readbuf[i];
                if (c == '\n' || linelen >= sizeof(linebuf) - 1) {
                    linebuf[linelen] = 0;
                    emit_line(env, onLine, onLineMid, linebuf, (int)linelen);
                    linelen = 0;
                    if (c == '\n') continue;
                }
                if (c != '\r') linebuf[linelen++] = c;
            }
        }
        int status = 0;
        pid_t w = waitpid(pid, &status, WNOHANG);
        if (w == pid) {
            /* drain remaining */
            for (;;) {
                ssize_t n = read(pipefd[0], readbuf, sizeof(readbuf));
                if (n <= 0) break;
                for (ssize_t i = 0; i < n; i++) {
                    char c = readbuf[i];
                    if (c == '\n' || linelen >= sizeof(linebuf) - 1) {
                        linebuf[linelen] = 0;
                        emit_line(env, onLine, onLineMid, linebuf, (int)linelen);
                        linelen = 0;
                        if (c == '\n') continue;
                    }
                    if (c != '\r') linebuf[linelen++] = c;
                }
            }
            if (linelen > 0) {
                linebuf[linelen] = 0;
                emit_line(env, onLine, onLineMid, linebuf, (int)linelen);
            }
            close(pipefd[0]);
            if (WIFEXITED(status)) return WEXITSTATUS(status);
            if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
            return -1;
        }
    }
    close(pipefd[0]);
    int status = 0;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}
