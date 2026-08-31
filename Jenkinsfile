pipeline {
    agent any

    environment {
        WILDFLY_DEPLOY = 'C:\\wildfly-18.0.1.Final\\standalone\\deployments'
        WAR_NAME       = 'rh_web_admin.war'
        TARGET_JAR     = 'rewrite-servlet-3.4.2.Final.jar'
    }

    stages {

        stage('COMPILE RewriteFilter') {
            tools {
                jdk 'JDK 17'
            }

steps {
    bat '''
        @echo off
        setlocal enabledelayedexpansion

        echo ========================================
        echo Directorio actual
        echo ========================================
        cd

        echo.
        echo ========================================
        echo Limpiando compilacion anterior
        echo ========================================

        if exist build rmdir /S /Q build
        mkdir build
        mkdir build\\classes

        echo.
        echo ========================================
        echo Buscando RewriteFilter.java REAL
        echo ========================================

        set SOURCE_FILE=

        for /F "delims=" %%F in ('dir /S /B RewriteFilter.java 2^>nul') do (
            echo Encontrado: %%F
            set SOURCE_FILE=%%F
        )

        if not defined SOURCE_FILE (
            echo.
            echo ERROR: No existe RewriteFilter.java en el workspace
            exit /b 1
        )

        echo.
        echo ========================================
        echo Archivo que se compilara
        echo ========================================
        echo !SOURCE_FILE!

        if not exist "!SOURCE_FILE!" (
            echo ERROR: El archivo seleccionado no existe
            exit /b 1
        )

        echo.
        echo ========================================
        echo Compilando RewriteFilter.java
        echo ========================================

        javac ^
          -cp "lib\\*" ^
          -sourcepath src ^
          -d build\\classes ^
          "!SOURCE_FILE!"

        if errorlevel 1 exit /b 1

        echo.
        echo ========================================
        echo Classes generadas
        echo ========================================

        dir /S /B build\\classes\\*.class

        endlocal
    '''
}
        }

        stage('EXTRACT WAR') {
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    if exist war_tmp rmdir /S /Q war_tmp
                    mkdir war_tmp

                    echo ========================================
                    echo Extrayendo WAR actual
                    echo ========================================

                    cd war_tmp
                    jar -xf "%WILDFLY_DEPLOY%\\%WAR_NAME%"
                    cd ..

                    if not exist "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" (
                        echo ERROR: No existe %TARGET_JAR% dentro del WAR
                        exit /b 1
                    )

                    echo.
                    echo JAR encontrado:
                    dir "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%"
                '''
            }
        }

        stage('PATCH JAR') {
            when {
        expression { false }
    }
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo Antes del patch
                    echo ========================================

                    jar -tf "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" | findstr /I "RewriteFilter.class"

                    echo.
                    echo ========================================
                    echo Reemplazando RewriteFilter.class
                    echo ========================================

                    jar -uf "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" ^
                        -C build\\classes org

                    if errorlevel 1 exit /b 1

                    echo.
                    echo ========================================
                    echo Verificando JAR modificado
                    echo ========================================

                    jar -tf "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" | findstr /I "RewriteFilter.class"

                    if errorlevel 1 (
                        echo ERROR: RewriteFilter.class no se encontro dentro del JAR
                        exit /b 1
                    )
                '''
            }
        }

        stage('BUILD PATCHED WAR') {
            when {
        expression { false }
    }
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    if exist patched rmdir /S /Q patched
                    mkdir patched

                    echo ========================================
                    echo Reconstruyendo WAR
                    echo ========================================

                    jar -cf "patched\\%WAR_NAME%" -C war_tmp .

                    if errorlevel 1 exit /b 1

                    echo.
                    echo WAR generado:
                    dir "patched\\%WAR_NAME%"
                '''
            }
        }

        stage('DEPLOY') {
            when {
        expression { false }
    }
            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo Backup del WAR actual
                    echo ========================================

                    if exist "%WILDFLY_DEPLOY%\\%WAR_NAME%.bak" (
                        del /Q "%WILDFLY_DEPLOY%\\%WAR_NAME%.bak"
                    )

                    copy /Y ^
                        "%WILDFLY_DEPLOY%\\%WAR_NAME%" ^
                        "%WILDFLY_DEPLOY%\\%WAR_NAME%.bak"

                    if errorlevel 1 exit /b 1

                    echo.
                    echo ========================================
                    echo Desplegando WAR parcheado
                    echo ========================================

                    copy /Y ^
                        "patched\\%WAR_NAME%" ^
                        "%WILDFLY_DEPLOY%\\%WAR_NAME%"

                    if errorlevel 1 exit /b 1

                    echo.
                    echo Patch desplegado correctamente.
                '''
            }
        }
    }

    post {
        success {
            echo 'RewriteFilter compilado, parcheado dentro del JAR y desplegado.'
        }

        failure {
            echo 'ERROR durante el patch. Revisar el log antes de tocar el WAR de produccion.'
        }
    }
}