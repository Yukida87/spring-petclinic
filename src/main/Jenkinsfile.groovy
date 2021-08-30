node {
    def mvnHome
    stage('Preparation') { // for display purposes
        // Get some code from a GitHub repository
        git(
            url: 'https://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@github.com/Yukida87/spring-petclinic',
            branch: 'main'
            )
        // Get the Maven tool.
        // ** NOTE: This 'M3' Maven tool must be configured
        // **       in the global configuration.
        mvnHome = tool 'M3'
    }
    stage('Build') {
        // Run the maven build
        withEnv(["MVN_HOME=$mvnHome"]) {
            sh '"$MVN_HOME/bin/mvn" -Dmaven.test.failure.ignore clean package findbugs:findbugs'
            }
        }
    stage('Results') {
        junit '**/target/surefire-reports/TEST-*.xml'
        def findbugs = scanForIssues tool: [$class: 'FindBugs'], pattern: '**/target/findbugsXml.xml'
        publishIssues issues:[findbugs]
        archiveArtifacts 'target/*.jar'
        dir('src/test/jmeter') {
            stash includes: 'petclinic_test_plan.jmx', name: 'performance'
        }
    }
}

node('master'){
    stage('Deployment'){
        build job: 'Petclinic SSH', parameters: [string(name: 'ARTIFACT_PROJECT', value: 'Petclinic Pipeline')]
        build job: 'Healthcheck', parameters: [string(name: 'HOST', value: 'http://192.168.178.24:8080')]
    }
    stage('Tests'){
        parallel(Frontend:{
            git url: 'http://192.168.178.24:8000/root/geb-tests-petclinic', credentialsId: 'GitHub-Private'
            bat 'gradlew.bat chromeTest'
            junit 'build/test-results/chromeTest/*.xml'
        }, Performance:{
            unstash 'performance'
            bzt 'petclinic_test_plan.jmx'
            })
    }
    stage('Production') {
        // Benutzereingabe
        input 'Ready for PRODUCTION?'
        // ältere Builds können nicht überholen
        milestone()
        // nur ein Build gleichzeitig
        lock('PROD') {
            node {
                echo 'Deploying!'
            }
        }
    }
}