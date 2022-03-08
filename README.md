# Spring Boot with Cert Manager Example

This repo demonstrate a means to use [Cert Manager](https://cert-manager.io/) to manage certificates for a [Spring Boot](https://spring.io/projects/spring-boot) application inside a kubernetes cluster.

## Componenets

This repo contains the following components:
* helloworld - This is a simple hello world Spring Boot web service, exactly the hello world service from Spring [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/), but with TLS enabled.
* init-keystore - Used as an init container for the helloworld web service. It generates the keystore used by the helloworld service.
* cert-monitor - A simple Java application (created with [JBang](https://www.jbang.dev/)) that monitors the certificate created by Cert Manager.

## How does it work

1. The helloworld Spring Boot web service (will be referred as "the web service") has SSL/TLS enabled.
2. The web service is configured to use a keystore located in a mount volume.
3. A init-keystore init container is created for the web service, it will use the customer resource Certificate from Cert Manager to create the keystore in the mount volume, so that the web service can use the generated certificate.
4. The cert-monitor keeps watching the Certificate object, and:
   * If the certificate is expiring (the duration is configurable), it will send an alert email to the specific email address.
   * After the certificate is renewed, it rollout restarts the web service, so that it can make use of the renewed certificate.

## How to use this repo

Note: this repo is just for demostration, it has only been very limited tested. I only tested this on a Mac Pro with macOS 10.15.7.

1. Prepare a kubernetes cluster.
   I only tested this with minikube v1.25.2.
   ```bash
   $ minikube start --driver=docker
   $ minikube addons enable registry # use a local registry
   ```
2. Clone this repo to your machine, and `cd` to the project root folder.
3. Follow [this guide](https://minikube.sigs.k8s.io/docs/handbook/registry/) to make it possible to push docker images to the local registry. For you convenience, there is a script named `expose-registry` in the `hack` folder, you can run that script and it will do the tricks for you. Note: do not close the terminal window/tab, open a new window/tab to run the rest steps.
4. Build the helloworld web service. Enter the helloworld folder, and build the docker image, push the image to the registry:
   ```bash
   $ cd helloworld
   $ ./mvnw spring-boot:build-image
   $ docker tag helloworld:0.0.1-SNAPSHOT localhost:5000/helloworld:latest
   $ docker push localhost:5000/helloworld:latest
   ```
5. Build the init-keystore image.
   ```bash
   $ # in the root folder
   $ docker build -t init-keystore ./init-keystore/
   $ docker tag init-keystore localhost:5000/init-keystore
   $ docker push localhost:5000/init-keystore
   ```
6. Build the cert-monitor image.
   ```bash
   $ # in the root folder
   $ docker build -t cert-monitor ./cert-monitor/
   $ docker tag cert-monitor localhost:5000/cert-monitor
   $ docker push localhost:5000/cert-monitor
   ```
7. Install Cert Manager. Follow the [official Installation](https://cert-manager.io/docs/installation/). I used Helm v3:
   ```bash
   $ helm repo add jetstack https://charts.jetstack.io
   $ help repo update
   $ helm install \
       cert-manager jetstack/cert-manager \
       --namespace cert-manager \
       --create-namespace \
       --version v1.7.1 \
       --set installCRDs=true
   ```
8. Create a namespace. Currently, namespace is hardcoded with `helloworld` in the YAML files.
   ```bash
   $ kubectl namespace create helloworld
   ```
9. Create an Issuer. I only used self signed issuer, you can follow the [doc](https://cert-manager.io/docs/configuration/) for other issuers.
   ```bash
   $ kubectl apply -f resources/self-signed-issuer.yaml
   ```
10. Create a self signed Certificate.
    ```bash
    $ kubectl apply -f resources/cert.yaml # you might want to review the YAML file before running the command
    ```
11. Deploy the helloworld web service.
    ```bash
    $ kubectl apply -f resources/helloworld-springboot.yaml
    ```
    A `LoadBalancer` Service will be created for the helloworld. In order to access the service, you need to start the minikube tunnel by
    ```bash
    $ kubectl tunnel
    ```
    Now, you can open `https://localhost:8080/greeting` in your browser. You will can a security warning because of the self signed certificate. Also, you should be able to view the certificate in your browser.
12. Deploy the cert-monitor.
    There are a few things you can configure for the service, please check the environment variables defined in the `cert-monitor.yaml` file. Before you can deploy it, you will need to create a Secret that holds the credentials for the email alerts. For you convenience, a script is provided, just run:
    ```bash
    $ ./hacks/create-email-credentials
    ```
    Once the Secret is created, you can deploy the cert-monitor by:
    ```bash
    $ kubectl apply -f resources/cert-monitor.yaml
    ```
    If you configure the email configuration correctly, you will be receiving the certificate expiring alert email.
