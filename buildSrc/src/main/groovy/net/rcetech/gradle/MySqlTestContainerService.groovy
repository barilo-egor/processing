package net.rcetech.gradle

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.testcontainers.mysql.MySQLContainer

abstract class MySqlTestContainerService implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private MySQLContainer container

    synchronized String createDatabase(String database) {
        if (container == null) {
            def thread = Thread.currentThread()
            def contextClassLoader = thread.contextClassLoader
            thread.contextClassLoader = MySqlTestContainerService.classLoader
            try {
                container = new MySQLContainer('mysql:8.0.46')
                container.start()
            } finally {
                thread.contextClassLoader = contextClassLoader
            }
        }
        def sql = "CREATE DATABASE IF NOT EXISTS `${database}`; GRANT ALL ON `${database}`.* TO '${container.username}'@'%';"
        def result = container.execInContainer('mysql', '-uroot', "-p${container.password}", '-e', sql)
        if (result.exitCode != 0) {
            throw new IllegalStateException("Не удалось создать базу ${database}: ${result.stderr}")
        }
        "jdbc:mysql://${container.host}:${container.getMappedPort(MySQLContainer.MYSQL_PORT)}/${database}"
    }

    synchronized String getUsername() {
        container.username
    }

    synchronized String getPassword() {
        container.password
    }

    @Override
    synchronized void close() {
        container?.stop()
    }
}
