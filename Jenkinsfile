pipeline {
	
    agent {
	    kubernetes {
	        // Change the name of jenkins-maven label to be able to use yaml configuration snippet
	        label "maven-jenkins"
	        // Inherit from Jx Maven pod template
	        inheritFrom "maven"
	        // Add scheduling configuration to Jenkins builder pod template
	        yaml """
spec:
  nodeSelector:
    cloud.google.com/gke-preemptible: true

  # It is necessary to add toleration to GKE preemtible pool taint to the pod in order to run it on that node pool
  tolerations:
  - key: gke-preemptible
    operator: Equal
    value: true
    effect: NoSchedule
    
# Create sidecar container with gsutil to publish chartmuseum index.yaml to Google bucket storage 
  volumes:
  - name: gsutil-volume
    secret:
      secretName: gsutil-secret
      items:
      - key: .boto
        path: .boto
  containers:
  - name: cloud-sdk
    image: google/cloud-sdk:alpine
    command:
    - /bin/sh
    - -c
    args:
    - gcloud config set pass_credentials_to_gsutil false && cat
    workingDir: /home/jenkins
    securityContext:
      privileged: false
    tty: true
    resources:
      requests:
        cpu: 128m
        memory: 256Mi
      limits:
    volumeMounts:
      - mountPath: /home/jenkins
        name: workspace-volume
      - name: gsutil-volume
        mountPath: /root/.boto
        subPath: .boto
"""        
		} 
    }
    
    environment {
      ORG               = "introproventures"
      APP_NAME          = "example-cloud-connector"
      CHARTMUSEUM_CREDS = credentials("jenkins-x-chartmuseum")
      CHARTMUSEUM_GS_BUCKET = "introproventures"
    }
    stages {
      stage("CI Build and push snapshot") {
        when {
          branch "PR-*"
        }
        environment {
          PREVIEW_VERSION = "0.0.0-SNAPSHOT-$BRANCH_NAME-$BUILD_NUMBER"
          PREVIEW_NAMESPACE = "$APP_NAME-$BRANCH_NAME".toLowerCase()
          HELM_RELEASE = "$PREVIEW_NAMESPACE".toLowerCase()
        }
        steps {
          container("maven") {
          
            sh "make preview-version"

            sh "make install"
            
            sh "make skaffold/preview"
            
            sh "make helm/preview"
          }
          
        }
      }
      stage("Build Release") {
        when {
          branch "develop"
        }
        environment {
      	  RELEASE_BRANCH    = "develop"
	      CHART_REPOSITORY  = "http://jenkins-x-chartmuseum:8080" 
	      GITHUB_CHARTS_REPO    = "https://github.com/igdianov/helm-charts.git"
        }
        steps {
          container("maven") {
            // ensure we're not on a detached head
            sh "make checkout"

            // so we can retrieve the version in later steps
            sh "make next-version"
            
            // Let's test first
            sh "make install"

            // Let's build and lint Helm chart
            sh "make helm/build"

            // Let's make tag in Git
            sh "make tag"
            
            // Let's deploy to Nexus
            sh "make deploy"
            
            // Let's build and push Docker image
            sh "make skaffold/release"

            // Let's release chart into Chartmuseum
            sh "make helm/release"
            
            // Let's release chart into Github repository
            sh "make helm/github"
            
          }
          container("cloud-sdk") {
            // Let's update index.yaml in Chartmuseum storage bucket
            sh "curl --fail -L ${CHART_REPOSITORY}/index.yaml | gsutil cp - gs://${CHARTMUSEUM_GS_BUCKET}/index.yaml"
          }
          
        }
      }
      stage("Promote to Environments") {
        when {
          branch "develop"
        }
        environment {
	        PROMOTE_HELM_REPO_URL = "https://storage.googleapis.com/$CHARTMUSEUM_GS_BUCKET"
        }

        steps {
            container("maven") {
	          // Let's make changelog in Github
	          sh "make changelog"
            
              // promote through all 'Auto' promotion Environments
              sh "make helm/promote"
            }
          }
        }
    }
    post {
        always {
            cleanWs()
        }
/*
        failure {

		input """Pipeline failed. 
We will keep the build pod around to help you diagnose any failures. 

Select Proceed or Abort to terminate the build pod"""
        }
*/	

    }
}
