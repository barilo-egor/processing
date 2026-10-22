// Требуется наличие следующих переменных в Jenkins:
// - SSH_CRED_ID - идентификатор SSH ключа
// - PROCESSING_DEPLOY_PATH - путь на сервере, куда необходимо расположить собранный проект
// - PROCESSING_DEPLOY_HOST - IP адрес сервера, на который будет отправлен проект
// - SSH_USER - пользователь SSH
// - SSH_PORT - порт SSH
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                sh 'chmod +x gradlew'
                sh './gradlew clean test :processing-app:bootJar :processing-app:installFrontend --no-daemon'
            }
        }
        stage('Deploy') {
            steps {
                sshagent([env.SSH_CRED_ID]) {
                    sh "scp -P ${SSH_PORT} build/libs/processing.jar ${SSH_USER}@${PROCESSING_DEPLOY_HOST}:${PROCESSING_DEPLOY_PATH}/"
                    sh "rsync -rlt --delete --chmod=Dug=rwx,Fug=rw,o= -e 'ssh -p ${SSH_PORT}' config/frontend/ ${SSH_USER}@${PROCESSING_DEPLOY_HOST}:${PROCESSING_DEPLOY_PATH}/config/frontend/"
                    sh "ssh -p ${SSH_PORT} ${SSH_USER}@${PROCESSING_DEPLOY_HOST} 'cd /srv/processing && docker rollout --timeout 180 processing'"
                }
            }
        }
    }

    post {
        success {
            echo 'Сборка и деплой успешно завершены!'
        }
        failure {
            echo 'Ошибка при сборке или деплое.'
        }
    }
}
