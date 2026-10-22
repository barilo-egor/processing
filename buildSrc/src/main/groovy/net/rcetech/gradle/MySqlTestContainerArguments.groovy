package net.rcetech.gradle

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

class MySqlTestContainerArguments implements CommandLineArgumentProvider {

    @Internal
    final Provider<MySqlTestContainerService> service

    @Input
    final String database

    MySqlTestContainerArguments(Provider<MySqlTestContainerService> service, String database) {
        this.service = service
        this.database = database
    }

    @Override
    Iterable<String> asArguments() {
        def mySql = service.get()
        def url = mySql.createDatabase(database)
        [
                "-Dprocessing.test.mysql.url=${url}".toString(),
                "-Dprocessing.test.mysql.username=${mySql.username}".toString(),
                "-Dprocessing.test.mysql.password=${mySql.password}".toString()
        ]
    }
}
