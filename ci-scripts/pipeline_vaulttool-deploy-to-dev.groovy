#!/usr/bin/env groovy

String version = env.version

if (! version) {
    error 'Missing argument "version"'
}

String jenkinsAgent = 'jenkins-agent/stretch'
String groupId = 'se.su.it'
String projectName = 'vaulttool'

String artifacts = 'vaulttool,war'
String suaPackage = 'sua-vaulttool'
List<String> hosts = ['vault-dev-app01.it.su.se', 'vault-dev-app02.it.su.se']

node('agent') {

    stage("Cleanup workspace")
    {
        cleanWs()
    }

    docker.image(jenkinsAgent).inside('-v /local/jenkins/conf:/local/jenkins/conf -v /local/jenkins/libexec:/local/jenkins/libexec -v /etc/krb5.conf:/etc/krb5.conf -v /etc/krb5.keytab-su-ci-prod:/etc/krb5.keytab-su-ci-prod') {

        stage("Checkout")
        {
            suCheckoutCode ([
                projectName : projectName,
            ])
        }

        stage("Check artifact in Nexus")
        {
            ret = sh (script: "/local/jenkins/libexec/manage-nexus-artifact ${groupId} ${version} ${artifacts} no-download", returnStatus: true)
            if (ret) {
                error "Artifact does not exist in Nexus, please build it before running this job."
            }
            else {
                println "Artifact exists in Nexus. Continuing."
            }
        }

        stage("Trigger build of SUA-packages")
        {

                println "Building SUA-package."
                suTriggerSuaBuild ([
                        artifacts: artifacts,
                        groupId: groupId,
                        suaPackage: suaPackage,
                        version: version,
                        wait: true
                ])
                println "SUA-package built."

        }

        stage("Deploy to dev")
        {
            suGetKerberosCredentials()

            String aptFile = '/etc/apt/sources.list.d/vaulttoolJenkins.list'
            hosts.each { String host ->
                String sshCommand = 'ssh -o ConnectTimeout=5 -o GSSAPIAuthentication=yes -o GSSAPIKeyExchange=yes -lroot ' + host

                sh sshCommand + " \"echo 'deb [arch=amd64] http://linux-sua.it.su.se/xenial/sua-v2 dev main' > ${aptFile}\""
                sh sshCommand + " apt-get update"
                sh sshCommand + " apt-get install --yes --force-yes -o Dpkg::Options::='--force-confdef' -o Dpkg::Options::='--force-confold' " + suaPackage
                sh sshCommand + " pkill -9 java"
                sh sshCommand + " systemctl restart " + suaPackage + ".service"
                sh sshCommand + " rm " + aptFile + " && apt-get update"
            }
        }
    }
}
