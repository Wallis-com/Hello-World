def construct_JSON(){
	
      def BRANCH_NAME = env.BRANCH_NAME
      def pom = readMavenPom file: 'pom.xml'
      def pomv = pom.properties['revision']	
      withEnv(["POM_BN=$BRANCH_NAME"]) {	

if(BRANCH_NAME == 'master'){
		def data = readJSON text: '{}'
		data.PROJECT_VERSION = "${pomv}" as String
                data.SHA1 = "" as String
		data.CHANGELIST = "" as String
                data.CHECKS = "Y" as String
		data.NEXUS_UPLOAD = "Y" as String
		data.NEXUS_REPO = "maven-releases" as String
	        if (BRANCH_NAME=='master'){
		    data.BLACKDUCK_PROJECT_VERSION="Master"
		}
                writeJSON file: 'project_verification.json', json: data, pretty: 4
	
} else if(BRANCH_NAME == 'develop' || BRANCH_NAME =~ 'release_'){
		def data = readJSON text: '{}'
		data.PROJECT_VERSION = "${pomv}-SNAPSHOT" as String		
                data.SHA1 = "" as String
		data.CHANGELIST = "-SNAPSHOT" as String
                data.CHECKS = "Y" as String
		data.NEXUS_UPLOAD = "Y" as String
		data.NEXUS_REPO = "maven-snapshots" as String
	        if (BRANCH_NAME=='develop'){
		    data.BLACKDUCK_PROJECT_VERSION="Develop"
		}
                writeJSON file: 'project_verification.json', json: data, pretty: 4
	
} else if(BRANCH_NAME =~ 'feature_' || BRANCH_NAME =~ 'hotfix_'){
		def data = readJSON text: '{}'
		data.PROJECT_VERSION = "${pomv}-${BRANCH_NAME}-SNAPSHOT" as String		
		data.SHA1 = "-${BRANCH_NAME}" as String
		data.CHANGELIST = "-SNAPSHOT" as String
                data.CHECKS = "Y" as String
		data.NEXUS_UPLOAD = "Y" as String
		data.NEXUS_REPO = "maven-snapshots" as String
	        if(BRANCH_NAME =~ 'feature_Blackduck*'){
                data.BLACKDUCK_PROJECT_VERSION= "$BRANCH_NAME"+"_"+new Date().format("MM/dd/yyyy_HH:mm:ss") as String
                }
                writeJSON file: 'project_verification.json', json: data, pretty: 4

} else if(BRANCH_NAME =~ 'PR'){
		def data = readJSON text: '{}'
		data.PROJECT_VERSION = "${pomv}-${BRANCH_NAME}-SNAPSHOT" as String		
		data.SHA1 = "-${BRANCH_NAME}" as String
		data.CHANGELIST = "-SNAPSHOT" as String
                data.CHECKS = "Y" as String
		data.NEXUS_UPLOAD = "N" as String
		data.NEXUS_REPO = "maven-snapshots" as String
                writeJSON file: 'project_verification.json', json: data, pretty: 4	
} else {
	echo "branch not configured"
}
}	
}
def nexusUpload(){
	if(env.BRANCH_NAME == 'master'){
	    sh "mvn -B -s $MAVEN_SETTINGS -f ${WORKSPACE}/pom.xml versions:set -DnewVersion=${POM_NEXUS_UPLOAD_VERSION}-SNAPSHOT -DgenerateBackupPoms=false"
	    sh "mvn -B -s $MAVEN_SETTINGS -f ${WORKSPACE}/pom.xml versions:set -DnewVersion=${POM_NEXUS_UPLOAD_VERSION} -DgenerateBackupPoms=false"
	}else{
	    sh "mvn -B -s $MAVEN_SETTINGS -f ${WORKSPACE}/pom.xml versions:set -DnewVersion=${POM_NEXUS_UPLOAD_VERSION} -DgenerateBackupPoms=false"
	}
	sh "mvn -B -s $MAVEN_SETTINGS deploy:deploy-file -DgeneratePom=false -DrepositoryId=${POM_NEXUS_REPO} -Durl=https://nexus.luigi.worldpay.io/repository/${POM_NEXUS_REPO}/ -DpomFile=${WORKSPACE}/pom.xml  -Dversion=${POM_NEXUS_UPLOAD_VERSION} -Dfile=${WORKSPACE}/pom.xml"		   	
	def modules = sh(script: "bash moduleReader.sh", returnStdout: true).trim()                         
        String[] modulesArray = modules.trim().split("\\s*,\\s*");
	for(int i in modulesArray) {
	    println(i);
	    if (i != 'tests'){
		sh "md5sum `find ./"+i+"/target -maxdepth 1 -type f` "				     
		sh "sha512sum `find ./"+i+"/target -maxdepth 1 -type f`"					     
		def packaging = sh(script: "mvn -B -s $MAVEN_SETTINGS -f ./"+i+"/pom.xml org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate -Dexpression=project.packaging -q -DforceStdout", returnStdout: true).trim()
		sh "mvn -B -s $MAVEN_SETTINGS deploy:deploy-file -DgeneratePom=false -DrepositoryId=${POM_NEXUS_REPO} -Durl=https://nexus.luigi.worldpay.io/repository/${POM_NEXUS_REPO}/ -DpomFile=./"+i+"/pom.xml  -Dversion=${POM_NEXUS_UPLOAD_VERSION} -Dfile=./"+i+"/target/"+i+"-${POM_NEXUS_UPLOAD_VERSION}.${packaging}  -Dfiles=./"+i+"/target/"+i+"-${POM_NEXUS_UPLOAD_VERSION}-javadoc.${packaging} -Dclassifiers=javadoc -Dtypes=jar"
	    }
	}					     
	currentBuild.result = 'SUCCESS'	
}
return this