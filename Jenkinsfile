pipeline {
    agent any

    environment {
        WILDFLY_DEPLOY = 'C:\\wildfly-18.0.1.Final\\standalone\\deployments'
        WAR_NAME       = 'gra_web.war'
        TARGET_JAR     = 'rewrite-servlet-3.4.2.Final.jar'
        BACKUP_ROOT    = 'D:\\backup_gra_web'
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
                    echo Buscando RewriteFilter.java
                    echo ========================================

                    set SOURCE_FILE=

                    for /F "delims=" %%F in ('dir /S /B RewriteFilter.java 2^>nul') do (
                        echo Encontrado: %%F
                        set SOURCE_FILE=%%F
                    )

                    if not defined SOURCE_FILE (
                        echo ERROR: No existe RewriteFilter.java
                        exit /b 1
                    )

                    if not exist "!SOURCE_FILE!" (
                        echo ERROR: El archivo seleccionado no existe
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo Archivo que se compilara
                    echo ========================================
                    echo !SOURCE_FILE!

                    echo.
                    echo ========================================
                    echo Compilando RewriteFilter.java
                    echo ========================================

                    javac ^
                      --release 8 ^
                      -cp "lib\*;C:\wildfly-18.0.1.Final\modules\system\layers\base\javax\json\api\main\jakarta.json-api-1.1.6.jar" ^
                      -sourcepath src ^
                      -d build\classes ^
                      "!SOURCE_FILE!"

                    if errorlevel 1 (
                        echo ERROR: Fallo la compilacion
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo Classes generadas
                    echo ========================================

                    dir /S /B build\\classes\\*.class

                    echo.
                    echo ========================================
                    echo Verificando RewriteFilter.class
                    echo ========================================

                    if not exist "build\\classes\\org\\ocpsoft\\rewrite\\servlet\\RewriteFilter.class" (
                        echo ERROR: No se genero RewriteFilter.class esperado
                        exit /b 1
                    )

                    endlocal
                '''
            }
        }


        stage('EXTRACT ORIGINAL WAR') {
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo Limpiando directorio temporal
                    echo ========================================

                    if exist war_tmp rmdir /S /Q war_tmp
                    mkdir war_tmp

                    echo.
                    echo ========================================
                    echo Verificando WAR original
                    echo ========================================

                    if not exist "%WILDFLY_DEPLOY%\\%WAR_NAME%" (
                        echo ERROR: No existe:
                        echo %WILDFLY_DEPLOY%\\%WAR_NAME%
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo Extrayendo WAR original
                    echo ========================================

                    cd war_tmp

                    jar -xf "%WILDFLY_DEPLOY%\\%WAR_NAME%"

                    if errorlevel 1 (
                        echo ERROR: No se pudo extraer el WAR
                        exit /b 1
                    )

                    cd ..

                    echo.
                    echo ========================================
                    echo Verificando JAR objetivo
                    echo ========================================

                    if not exist "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" (
                        echo ERROR: No existe %TARGET_JAR% dentro del WAR
                        exit /b 1
                    )

                    dir "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%"
                '''
            }
        }


        stage('PATCH JAR') {
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo RewriteFilter ANTES del patch
                    echo ========================================

                    jar -tf "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" ^
                        | findstr /I "RewriteFilter.class"

                    echo.
                    echo ========================================
                    echo Aplicando patch al JAR
                    echo ========================================

                    jar -uf "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" ^
                        -C build\\classes ^
                        org\\ocpsoft\\rewrite\\servlet

                    if errorlevel 1 (
                        echo ERROR: No se pudo modificar %TARGET_JAR%
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo RewriteFilter DESPUES del patch
                    echo ========================================

                    jar -tf "war_tmp\\WEB-INF\\lib\\%TARGET_JAR%" ^
                        | findstr /I /C:"org/ocpsoft/rewrite/servlet/RewriteFilter.class"

                    if errorlevel 1 (
                        echo ERROR: RewriteFilter.class no existe despues del patch
                        exit /b 1
                    )
                '''
            }
        }


        stage('BUILD PATCHED WAR') {
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo Limpiando salida anterior
                    echo ========================================

                    if exist patched rmdir /S /Q patched
                    mkdir patched

                    echo.
                    echo ========================================
                    echo Reconstruyendo WAR parcheado
                    echo ========================================

                    jar -cf "patched\\%WAR_NAME%" -C war_tmp .

                    if errorlevel 1 (
                        echo ERROR: No se pudo generar el WAR
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo WAR generado
                    echo ========================================

                    dir "patched\\%WAR_NAME%"
                '''
            }
        }


        stage('VERIFY PATCHED WAR') {
            tools {
                jdk 'JDK 17'
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo Verificando WAR generado
                    echo ========================================

                    if not exist "patched\\%WAR_NAME%" (
                        echo ERROR: No existe patched\\%WAR_NAME%
                        exit /b 1
                    )

                    if exist verify_tmp rmdir /S /Q verify_tmp
                    mkdir verify_tmp

                    echo.
                    echo ========================================
                    echo Extrayendo WAR nuevo para verificar
                    echo ========================================

                    cd verify_tmp
                    jar -xf "..\\patched\\%WAR_NAME%"
                    cd ..

                    if not exist "verify_tmp\\WEB-INF\\lib\\%TARGET_JAR%" (
                        echo ERROR: El WAR nuevo no contiene %TARGET_JAR%
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo Verificando RewriteFilter dentro
                    echo del JAR del WAR NUEVO
                    echo ========================================

                    jar -tf "verify_tmp\\WEB-INF\\lib\\%TARGET_JAR%" ^
                        | findstr /I /C:"org/ocpsoft/rewrite/servlet/RewriteFilter.class"

                    if errorlevel 1 (
                        echo ERROR: RewriteFilter.class no esta dentro del WAR nuevo
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo WAR NUEVO VERIFICADO CORRECTAMENTE
                    echo ========================================
                '''
            }
        }


        stage('BACKUP ORIGINAL AND PATCHED') {
            steps {
                bat '''
                    @echo off
                    setlocal enabledelayedexpansion

                    echo ========================================
                    echo Preparando backup
                    echo ========================================

                    if not exist "%BACKUP_ROOT%" (
                        mkdir "%BACKUP_ROOT%"
                    )

                    for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do (
                        set "TIMESTAMP=%%I"
                    )

                    set "BACKUP_DIR=%BACKUP_ROOT%\\!TIMESTAMP!"

                    mkdir "!BACKUP_DIR!"

                    if errorlevel 1 (
                        echo ERROR: No se pudo crear:
                        echo !BACKUP_DIR!
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo Copiando WAR ORIGINAL
                    echo ========================================

                    copy /Y ^
                      "%WILDFLY_DEPLOY%\\%WAR_NAME%" ^
                      "!BACKUP_DIR!\\gra_web_original.war"

                    if errorlevel 1 (
                        echo ERROR: Fallo backup del WAR original
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo Copiando WAR NUEVO
                    echo ========================================

                    copy /Y ^
                      "patched\\%WAR_NAME%" ^
                      "!BACKUP_DIR!\\gra_web_patched.war"

                    if errorlevel 1 (
                        echo ERROR: Fallo backup del WAR parcheado
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo BACKUPS GENERADOS
                    echo ========================================

                    dir "!BACKUP_DIR!"

                    echo.
                    echo Backup ubicado en:
                    echo !BACKUP_DIR!

                    endlocal
                '''
            }
        }


        stage('DEPLOY') {

            /*
             * DESACTIVADO INTENCIONALMENTE.
             *
             * Cuando hayas verificado los dos WAR en D:\\backup_gra_web,
             * cambia false por true.
             */

            when {
                expression { true }
            }

            steps {
                bat '''
                    @echo off

                    echo ========================================
                    echo DESPLEGANDO WAR PARCHEADO
                    echo ========================================

                    copy /Y ^
                      "patched\\%WAR_NAME%" ^
                      "%WILDFLY_DEPLOY%\\%WAR_NAME%"

                    if errorlevel 1 (
                        echo ERROR: Fallo el deploy
                        exit /b 1
                    )

                    echo.
                    echo ========================================
                    echo DEPLOY COMPLETADO
                    echo ========================================

                    dir "%WILDFLY_DEPLOY%\\%WAR_NAME%"
                '''
            }
        }
    }


    post {
        success {
            echo 'RewriteFilter compilado, JAR parcheado, WAR generado y backups creados.'
            echo 'DEPLOY esta desactivado.'
        }

        failure {
            echo 'ERROR durante el proceso.'
            echo 'El WAR desplegado no deberia haber sido modificado.'
        }
    }
}