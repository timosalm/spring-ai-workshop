# Application Advisor 1.5

## Table of Contents

- [What is Application Advisor?](#what-is-application-advisor)
- [Application Advisor 1.5 Release Notes](#application-advisor-15-release-notes)
- [Downloading and Running Application Advisor](#downloading-and-running-application-advisor)
- [Application Advisor "How-To" Guides](#application-advisor-how-to-guides)
- [How is Application Advisor Different from Other Solutions?](#how-is-application-advisor-different-from-other-solutions)
- [Application Advisor Architecture](#application-advisor-architecture)
- [Integrating Application Advisor with CI/CD Pipelines](#integrating-application-advisor-with-cicd-pipelines)
- [Custom Upgrades Using Application Advisor](#custom-upgrades-using-application-advisor)
- [Recommendations Using Application Advisor](#recommendations-using-application-advisor)
- [Running Commercial Recipes Using OpenRewrite Tools](#running-commercial-recipes-using-openrewrite-tools)
- [Spring Support Insights in Tanzu Hub](#spring-support-insights-in-tanzu-hub)
- [IDE Integration Using Model Context Protocol (MCP)](#ide-integration-using-model-context-protocol-mcp)
- [Upgrade Guide for Application Advisor 1.3.x](#upgrade-guide-for-application-advisor-13x)
- [Troubleshooting Application Advisor](#troubleshooting-application-advisor)
- [Application Advisor CLI Reference](#application-advisor-cli-reference)

---

## What is Application Advisor?

Application Advisor is a VMware Tanzu Spring capability for continuously and incrementally upgrading Spring dependencies in all your Git repositories.

Application Advisor creates an upgrade pull request every time it detects that there is an opportunity to upgrade the dependencies of your Git repository.

Application Advisor is an upgrade orchestrator that prevents stale or invalid pull requests with automatic upgrades caused by dependency conflicts. Application Advisor ensures that upgrades are applied in the right order across your private repositories to prevent dependency conflicts.

### How Application Advisor Works

Application Advisor is a native CLI currently available on Linux, MacOS, and Windows. This CLI is responsible for:

- Generating the dependency tree and the build tool versions of a Git repository.
- Computing the upgrade plan, which is the list of Spring dependencies or tools that must be upgraded together (using OpenRewrite recipes) to the next release.
- Running the refactors that apply the corresponding dependency version changes and Java API upgrades, if needed, using OpenRewrite recipes.
- Creating pull requests with the refactors. The CLI needs a Git access token with write access to the repository.

It is assumed that the CLI is integrated into the CI/CD environment so that the Git repositories are continuously analyzed and upgraded to the next version, if necessary. The CI/CD environment is already configured to have access to internal Maven repositories, and to be able to resolve all the dependencies and compile the sources.

### What Spring Applications Can Be Upgraded

Application Advisor can upgrade any Spring (Framework or Boot) application or component that can be built/compiled via Maven (using a pom.xml file) or Gradle (using a build.gradle file).

Application Advisor supports upgrades from Spring Boot 1.0.x and Spring Framework 2.0.x until the latest and greatest versions. However, the most complete upgrades are from applications using Spring Boot 2.7.x or Spring Framework 5.8.x and beyond.

For every generation of Spring Boot 3.x, Application Advisor is gradually offering 100% upgrade coverage for API deprecations and breaking changes of Spring Boot, Spring Framework, Spring Security, Spring Data and Spring Integration. Application Advisor will also upgrade the rest of Spring projects dependencies, but is not including additional Java changes.

Third party OSS dependencies whose Java APIs are heavily impacted with the Spring Boot upgrade (e.g Jakarta, Springdoc, Apache Http Client) are also individually selected and updated with enterprise recipes.

Application Advisor upgrades are tested and supported for Java code, only. However, since Application Advisor is built on top of OpenRewrite, you can use it for Kotlin projects, but this is unsupported and experimental functionality.

### How to Start Using Application Advisor

To start using Application Advisor, we recommend that you follow the following guides:

- Upgrade Spring Boot from 2.7 to 4.0
- Upgrade a Spring application that uses a custom Spring Boot starter

---

## Application Advisor 1.5 Release Notes

These release notes apply to all Application Advisor 1.5.x releases.

### v1.5.4

**Release date:** January 12, 2026

**New features:**
- Adds mappings for Spring Cloud 2025.1.0

**Resolved issues:**
- Fixes an issue where the advice apply command uses JVM options.
- Fixes NPE when commands are run in an empty directory.

**Enhancements:**
- Bug fixes listed in Resolved issues
- Security fixes listed in Security fixes

### v1.5.3

**Release date:** December 16, 2025

**New features:**
- Support for Spring Boot 4.0.x upgrades
- Updates Spring Cloud upgrade mappings
- Includes detected projects and versions in the upgrade-plan get command.

**Resolved issues:**
- Provides additional warnings for errors in upgrade mappings

**Enhancements:**
- Bug fixes listed in Resolved issues
- Security fixes listed in Security fixes

### v1.5.2

**Release date:** November 18, 2025

**Resolved issues:**
- Fixes the error messages when the Enterprise Spring Maven repository is not correctly configured.
- Fixes an issue in the build-config publish command for publishing the commit date to Tanzu Hub.

**Enhancements:**
- Renames from Spring Application Advisor to Application Advisor.
- Better description for the new --accept-no-alignment option.

### v1.5.1

**Release date:** November 3, 2025

**New features:**
- New option --accept-no-alignment in the upgrade plan commands to upgrade to the latest versions even if dependencies are not aligned.

**Resolved issues:**
- Fixes a runtime issue identified running the Java 21 upgrade.

**Enhancements:**
- Updates the communication channel for support requests to support.broadcom.com.

### v1.5.0

**Release date:** October 28, 2025

**New features:**
- Spring App Advisor cf CLI plug-in is installed by default with Tanzu cf CLI
- Initial set of Spring Boot 4.0.x recipes
- Complete set of recipes for Spring Security 6.0.x

**Security fixes:**

#### 1.5.4
There are no security fixes in this release.

#### 1.5.3
**Component:** application-advisor  
**Vulnerabilities Resolved:** CVE-2024-38374

---

## Downloading and Running Application Advisor

The Application Advisor can be used as a native CLI or as a cf CLI plugin.

### Download the Native CLI

The CLI is currently available only for Linux, Windows, and MacOS.

**Note:** The "ARTIFACTORY_TOKEN" shown in the following examples refer to the token available on the Broadcom Support Portal. For instructions, see Spring Enterprise Repository for Artifact Repository Administrators in the Tanzu Spring documentation.

To download the CLI, run:

**For Linux:**
```bash
curl -L -H "Authorization: Bearer $ARTIFACTORY_TOKEN" -o advisor-cli.tar -X GET https://packages.broadcom.com/artifactory/spring-enterprise/com/vmware/tanzu/spring/application-advisor-cli-linux/1.5.4/application-advisor-cli-linux-1.5.4.tar
tar -xf advisor-cli.tar --strip-components=1 --exclude=./META-INF
```

**For Windows:**
```bash
curl -L -H "Authorization: Bearer $ARTIFACTORY_TOKEN" -o advisor-cli.tar -X GET https://packages.broadcom.com/artifactory/spring-enterprise/com/vmware/tanzu/spring/application-advisor-cli-windows/1.5.4/application-advisor-cli-windows-1.5.4.tar
tar -xf advisor-cli.tar --strip-components=1 --exclude=./META-INF
```

**For MacOS Intel:**
```bash
curl -L -H "Authorization: Bearer $ARTIFACTORY_TOKEN" -o advisor-cli.tar -X GET https://packages.broadcom.com/artifactory/spring-enterprise/com/vmware/tanzu/spring/application-advisor-cli-macos/1.5.4/application-advisor-cli-macos-1.5.4.tar
tar -xf advisor-cli.tar --strip-components=1 --exclude=./META-INF
```

**For MacOS ARM64:**
```bash
curl -L -H "Authorization: Bearer $ARTIFACTORY_TOKEN" -o advisor-cli.tar -X GET https://packages.broadcom.com/artifactory/spring-enterprise/com/vmware/tanzu/spring/application-advisor-cli-macos-arm64/1.5.4/application-advisor-cli-macos-arm64-1.5.4.tar
tar -xf advisor-cli.tar --strip-components=1 --exclude=./META-INF
```

### Run Application Advisor from the Tanzu cf CLI

If you are deploying your applications into Tanzu Platform you have two options:

1. Upgrade your Elastic Application Runtime foundation to the latest version and automatically upgrade the Tanzu cf CLI, which includes the cf login command.

2. Manually download the latest version of the Tanzu cf CLI from the Broadcom Support Portal and add the plugin that is in the same bundle to include the upgrade capabilities. To download the latest cf CLI, follow the next steps:
   - Login into the Broadcom Support Portal
   - Select Tanzu from the top-level menu.
   - Select the My Downloads section in the left side menu.
   - Search for "Elastic Application Runtime."
   - Click Elastic Application Runtime.
   - Select the latest release.
   - Select "CF CLI" from the table.
   - Install the repo plugin to enable the upgrade capabilities of App Advisor as follows:
     ```bash
     tanzu plugin install all --local-source <downloaded-plugin>
     ```

### Configure the Maven Settings to Download the Commercial Recipes

For you to be able to upgrade your Spring Applications, the Application Advisor CLI must be able to download artifacts from Spring Maven Enterprise repository. Ensure that your Maven repositories are configured correctly. See Running commercial recipes using OpenRewrite tools.

### Produce a Build Configuration

A build configuration contains:
- The dependency tree using the CycloneDX format
- The Java version required to compile the sources
- The build tool versions

To produce the build configuration, run:

```bash
advisor build-config get
```

or equivalently, you can run:

```bash
cf repo build-sbom
```

This command produces this output:

```
Resolving the build configuration of $path.
🏃 [ 1 / 3 ] Resolving dependencies with "maven/gradle command" [3m 2s] ok
🏃 [ 2 / 3 ] Resolving JDK version [4s]
🏃 [ 3 / 3 ] Resolving build tool [1s]
🚀 Build configuration generated at $path/.advisor/build-config.json
💔 Errors
- $repo failed with the following message:
The maven command failed. You can find the error in .advisor/errors/${error-id}.log
```

The build configuration is produced as a JSON file in an internal folder called .advisor. If the folder already contains a build configuration, it will be overwritten.

### Generate an Upgrade Plan

This command provides the step-by-step upgrade plan showing the Spring projects that need to be upgraded, and to what versions.

```bash
advisor upgrade-plan get
```

or equivalently:

```bash
cf repo upgrade-plan
```

The output looks something like this:

```
Fetching details for upgrade plan:
- Step 1:
  * Upgrade Spring Boot from v2.6.1 to v2.7.x
  * Upgrade Spring Framework from v3.5.1 to v4.0.x
- Step 2:
  * Upgrade Java from 8 to 11
- Step 3:
  * Upgrade Spring Boot from v 2.7.1 to v3.0.x
```

### Apply an Upgrade Plan from Your Local Machine

The following command can upgrade the files locally on your machine. Then you can manually review them to decide if you want to integrate Application Advisor pull requests into your repository.

```bash
advisor upgrade-plan apply
```

or equivalently:

```bash
cf repo apply-upgrade-plan
```

Application Advisor preserves your coding style by making the minimum required changes in the source files. However, if you are using a Maven or Gradle formatter like spring-javaformat for your repository, add the --after-upgrade-cmd option to the advisor upgrade-plan apply command as follows:

```bash
advisor upgrade-plan apply --after-upgrade-cmd=${MAVEN_OR_GRADLE_FORMATTER_TASK}
```

For example, for spring-javaformat, use:

```bash
advisor upgrade-plan apply --build-tool-run-cmd=spring-javaformat:apply
```

### Increasing Memory Limit

The Advisor CLI runs Gradle to get Build Configuration and apply recipes.

Gradle will run a separate process, daemon, to use the local configuration, depending on the project. The Java VM used by the daemon limits memory to 512 MegaBytes by default. However, it can also provide other default options.

When the target project to upgrade is large, it may be necessary to increase the default memory limit. For a Gradle build, use org.gradle.vmargs.

For example, if you want to increase the memory limit to 1 GigaByte, run:

```bash
upgrade-plan apply --url <url> --build-tool-jvm-args="-Dorg.gradle.jvmargs=-Xmx1g"
```

You can also change the Garbage Collector:

```bash
upgrade-plan apply --url <url> --build-tool-jvm-args="-Dorg.gradle.jvmargs=-Xmx2g -XX:+UseParallelGC"
```

### Running on Air-Gapped Environments

The Advisor CLI runs Gradle to resolve build configurations and apply recipes.

By default it uses Maven Central plugin repository, but the default can be overwritten by setting the following environment variable:

```bash
ADVISOR_DEFAULT_OSS_PLUGINS_REPOSITORY=https://pluginrepository.acme.com/m2
```

### Enable Continuous and Incremental Upgrades

To enable continuous and incremental upgrades with automatic pull requests:

1. If you are using GitLab or GitHub for storing your Git repos, check that your pipelines are running the following command:
   ```bash
   advisor upgrade-plan apply --push --from-yml
   ```
   
   If not, integrate the following commands in your CI/CD pipeline:
   ```bash
   advisor build-config get
   advisor build-config publish
   advisor upgrade-plan apply --push --from-yml
   ```
   
   If you are using Bitbucket, follow the instructions in this guide to send pull requests in Bitbucket.

2. Verify or create the GIT_TOKEN_FOR_PRS environment variable for your CI/CD build. The value should be an access token with write access to the repository. Application Advisor creates a branch in the repository and makes a new pull request against the branch.

3. Add a file named .spring-app-advisor.yml in the root directory of your repository with the following contents:
   ```yaml
   enabled: true
   ```

**Note:** Ensure your current commit exists in the remote Git repository. If you are working on a local branch, push it first using git push.

---

## Application Advisor "How-To" Guides

You can use the following "How-to" topics to help you understand how to use Application Advisor:

- Upgrade Spring Boot from 2.7 to 4.0
- Upgrade a Spring application that uses a custom Spring Boot starter
- Upgrade guide for Application Advisor 1.3.x

### Upgrade Spring Boot from 2.7 to 4.0

This is a classic exercise that upgrades a Spring Boot 2.7.x application with Java 8 to the latest version of Spring Boot.

For this example, we are using a detached commit of an existing OSS repository called Spring Petclinic, a basic application that uses Spring Boot.

The main branch of this repository is already up to date with the latest Spring Boot version. For the sake of this example, we use a detached branch when the application was using Spring Boot 2.7.

```bash
git clone https://github.com/spring-projects/spring-petclinic
cd spring-petclinic
git branch advisor-demo 9ecdc1111e3da388a750ace41a125287d9620534
git checkout -f advisor-demo
```

The requirements for upgrading this repository are:

- The CLI is available in your $PATH. See Downloading and running Application Advisor.
- Minimum requirement: Java SDK 17 or higher is available. Recommended: Java SDK 8, 11, and 17 are available.
- You have a tool to manage multiple different Java versions. In this guide, we use sdkman, but you can use any tool available.

**Note:** If you previously ran Application Advisor in this repository and the build now fails with many Checkstyle or NoHttp errors, see Why does my build fail with Checkstyle or NoHttp errors in the .advisor directory?.

The first step is to generate the build configuration of your application, which means to generate all the information required to build it: the dependency tree (SBOM), the Java version, the build tool version, and the application modules. Run the following command:

```bash
advisor build-config get
```

The result of the command is:

```
Resolving the build configuration of spring-petclinic.
🏃 [ 1 / 3 ] Resolving dependencies with mvnw [00m 04s] ok
🏃 [ 2 / 3 ] Resolving JDK version [00m 02s] ok
🏃 [ 3 / 3 ] Resolving build tool [00m 01s] ok
🚀 The build-configuration has been generated in target/.advisor/build-config.json
```

The build configuration is a file required to resolve the upgrade plan of an application.

Run the following command:

```bash
advisor upgrade-plan get
```

This command prints the Spring Petclinic upgrade plan showing discovered projects and the upgrade steps.

Next, apply the upgrade plan. Run the following command to apply the first step, which is Upgrade java from 8 to 11. Before running the command, ensure that you have already configured the Spring Enterprise Maven repository.

```bash
advisor upgrade-plan apply
```

The command produces the following output:

```
🏃 [ 1 / 2 ] Fetching and processing upgrade plan details [00m 01s] ok
Projects to upgrade:
* java from 8 to 11
🔨 [ 2 / 2 ] Upgrading sources... [00m 25s] ok
👍 Successfully applied upgrade.
```

This produces a very small change. However, that is the value of the tool; it lets developers upgrade as much as they can without imposing an upgrade to the latest version of Spring. To review the changes, run the following command:

```bash
git diff
```

You can check that your application is still working using Java 11. Run the following commands:

```bash
sdk install java 11.0.25-tem
sdk use java 11.0.25-tem
./mvnw test
```

To continue applying the steps, consider committing the changes produced at each step. Run the following command to include the changes in the advisor-demo branch.

```bash
git add .
git commit -m "Upgrade java from 8 to 11"
```

To proceed with the upgrade to Java 17, repeat the same steps because the Java version has changed, and therefore the build configuration must be regenerated:

```bash
advisor build-config get && advisor upgrade-plan apply
```

Now, to evaluate the changes, continue with the following commands:

```bash
sdk install java 17.0.13-tem
sdk use java 17.0.13-tem
./mvnw test
```

You get the same result as in the previous upgrade, so you can commit the changes again.

```bash
git add .
git commit -m "Upgrade java from 11 to 17"
```

Now, you are ready to start upgrading to Spring Boot 3.0.x, which is an important upgrade because it replaces the javax packages with Jakarta.

```bash
advisor build-config get && advisor upgrade-plan apply
```

In this case, after you run git status, you see changes to multiple source files including model classes, controllers, and configuration files.

This concludes the second step of the upgrade plan. By running the command `advisor build-config get && advisor upgrade-plan apply` for each of the remaining steps and following the same pattern of git commands, the application is fully upgraded to the latest version of Spring Boot.

### Upgrade a Spring Application That Uses a Custom Spring Boot Starter

This is a classic exercise that upgrades a Spring Boot application called acme-bookings-app with a dependency called acme-boot-starter, which is a custom Spring Boot starter.

In this exercise, both the application (acme-bookings-app) and the starter (acme-boot-starter) depend transitively or directly on Spring. Therefore, running an OpenRewrite recipe to upgrade SpringBoot against acme-bookings-app will:

- Upgrade the corresponding Java sources to consume the new Spring APIs.
- Upgrade the defined Spring dependencies that appears in the pom.xml or the build.gradle file.

However, the recipe will NEVER bump the version of the acme-boot-starter dependency because OpenRewrite has no knowledge about:

- What dependencies (specially those that are internal) are using Spring?
- What versions of Spring are available in every version of every dependency?
- What is the correct version to upgrade the application given that some dependencies have not been released for every version of Spring?

Using Application Advisor, we learn that:

- Application Advisor prevents invalid dependency changes in acme-bookings-app. There will not be an upgrade plan for acme-bookings-app unless we publish the custom upgrade mappings for acme-boot-starter.
- By continuously updating your custom upgrade mappings of a project like acme-boot-starter, all the downstream dependencies can be automatically upgraded.

The requirements for upgrading acme-bookings-app are:

- The CLI available in your $PATH. See Downloading and running Application Advisor.
- Minimum requirement: Java SDK 17 or higher is available. Recommended: Java SDK 8, 11, and 17 are available.
- A tool to manage multiple different Java versions. In this guide, we use sdkman, but you can use any tool available.

To follow the example in this guide, clone acme-bookings-app and acme-boot-starter.

```bash
git clone https://github.com/Broadcom/acme-bookings-app
git clone https://github.com/Broadcom/acme-boot-starter
```

The next step is to build the artifacts of the different available versions of acme-boot-starter in your local machine. To do this, run the following commands from the acme-boot-starter directory:

```bash
git checkout 1.0.0
./mvnw install
git checkout 2.0.0
./mvnw install
```

These commands build the `com.acme.boot:acme-boot-starter:1.0.0` and `com.acme.boot:acme-boot-starter:2.0.0` artifacts and make them available into your local Maven repository, so they can be resolved to build acme-bookings-app. In a real world scenario, this step is not required because these artifacts are already available in a public or internal Maven repository.

Now, if you open the pom.xml of acme-bookings-app, you see the following dependency:

```xml
<dependency>
    <groupId>com.acme.boot</groupId>
    <artifactId>acme-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Verify that the dependency can be resolved by running the following command from the acme-booking-app directory:

```bash
./mvnw package
```

The acme-booking-app is using Spring Boot 2.7.x. To upgrade it to use the latest version of Spring Boot using Application Advisor:

Start by running the following commands from the acme-booking-app:

```bash
advisor build-config get
advisor upgrade-plan get
```

The last command produces this output:

```
The projects ["spring-framework", "spring-boot"] could not be included in the Upgrade Plan because they are used as transitive dependencies for other projects, and no upgrades are configured for them.

Please request your administrator to configure the projects of the following dependencies:
- com.acme.boot:acme-boot-starter
  uses:
    - spring-framework
    - spring-boot
  blocking upgrades for:
    - spring-boot

No upgrade plans available - your project seems to be up to date.
```

Application Advisor is notifying you that is not safe to upgrade this application until you provide information about how to upgrade com.acme.boot:acme-boot-starter; because otherwise there might be inconsistent Spring Boot versions between the one that is consumed by the acme-boot-starter and the acme-booking-app.

To tell Application Advisor how to upgrade acme-boot-starter from one version to another, you need to provide a custom upgrade mapping file. This file can initially be filled manually, according to the specification described, or using an experimental Application Advisor command. To try the experimental command, run the following from any empty directory.

```bash
advisor mapping build -r https://github.com/Broadcom/acme-boot-starter --offline
```

This command calculates the dependencies, the minimum Java version, and the submodules for each of the acme-boot-starter Git tags that have been released.

**Note:** You must use the --offline option. This enforces looking for the versions available in the local Maven repository that were previously built. Otherwise, the command looks in the remote Maven repository.

After running the command, mappings are generated showing version dependencies. If you look at the generated file, in the supportedGenerations properties, you will see the following conditions:

- acme-boot-starter:1.0.0 requires spring-boot:2.7.x
- acme-boot-starter:2.0.0 requires spring-boot:3.0.x

The recipes properties contains an empty list. By default, when no recipes are defined, Application Advisor dynamically generates the recipes to bump the artifact versions.

To instruct Application Advisor to use this configuration, you must include all your projects in a custom mapping file.

To make this mapping file available, you can export the following environment variable from where the CLI is available:

```bash
cp .advisor/mappings/acme-boot-starter.json /demo/acme-mappings.json
export SPRING_ADVISOR_MAPPING_CUSTOM_0_FILEPATH=/demo/acme-mappings.json
```

Now that the mappings are available, you can request the upgrade plan for acme-booking-app (running `advisor upgrade-plan get`). The output now shows a proper upgrade plan including the acme-boot-starter upgrade.

After running the following command to see the changes to upgrade to Spring Boot 3.0.x:

```bash
advisor upgrade-plan apply
git diff
```

The changes include dependency version updates for spring-boot-starter-parent and acme-boot-starter and other minor configuration changes required to upgrade this application to Spring Boot 3.0.x.

**Note:** Spring Boot can be upgraded in all the steps without requiring upgrade of acme-boot-starter. This is because Application Advisor assumes semantic versioning, which means that the Spring libraries, acme-boot-starter and acme-booking-app should be compatible.

---

## How is Application Advisor Different from Other Solutions?

This section explains how Application Advisor is different from OpenRewrite, Moderne, and Spring Boot Migrator.

### OpenRewrite

Application Advisor runs on OpenRewrite, but there are some key differences when you use Application Advisor:

1. Developers do not need to understand, search, and compose the recipes they need to upgrade their applications. Application Advisor selects the recipes based on the project setup without exposing any OpenRewrite contracts.

2. Application Advisor includes increased coverage for Spring project upgrade paths with Tanzu Spring-provided recipes.

3. Application Advisor is an upgrade orchestrator. It prevents invalid upgrades when Spring is consumed as a transitive dependency if there are no associated OpenRewrite recipes. You will know when new recipes need to be created before upgrading. For instance, if your organization has a custom Spring starter located in a different repository than your application, Application Advisor does not upgrade applications using that starter if there are no recipes configured for it.

The following analogy between some Debian/Ubuntu Linux and Application Advisor commands illustrates the differences with OpenRewrite:

| Linux Command | Application Advisor Command | Description |
|--------------|----------------------------|-------------|
| apt-get update | advisor upgrade-plan get | Checks what projects need to be upgraded together |
| apt-get upgrade | advisor upgrade-plan apply | Performs the upgrade of the projects that need to be upgraded together |
| dpkg -i –force | mvn -U org.openrewrite.maven:rewrite-maven-plugin:run -Drewrite.activeRecipes=${recipe} | Performs an upgrade without analyzing if it will be valid with the existing projects |

### Moderne Platform

The Moderne Platform is a solution for running OpenRewrite with a specific recipe in multiple Git repositories simultaneously, and preview those results before generating a pull request. Therefore, it requires that a person or system proactively selects a recipe and has already analyzed the Git repositories that can receive that upgrade. It is not an upgrade orchestrator that upgrades repositories in the correct order.

### Spring Boot Migrator

Application Advisor is designed to replace Spring Boot Migrator. Spring Application Advisor supports only the use case for upgrading your Spring applications, but provides higher upgrade coverage than Spring Boot Migrator through use of Spring Commercial OpenRewrite recipes.

---

## Application Advisor Architecture

To help you understand how Application Advisor works and how it interacts with your environment and services, this topic:

- Explains how Application Advisor fits into your software delivery lifecycle (SDLC)
- Provides an architecture diagram that shows how data flows through the Application Advisor components and your system

### How Application Advisor Fits into Your Software Delivery Lifecycle (SDLC)

Application Advisor is designed to create automatic pull requests that incrementally upgrade your Spring applications. Pull requests are requests to review a new contribution to a repository. This is where the information is shared among reviewers, and where multiple manual and automatic checks are run to prevent causing a broken application.

Application Advisor creates a new branch in the Git repository every time a new upgrade opportunity is detected, so engineering team members with write access in the repository can review and adapt the requested changes before integrating them into the main branch.

Application Advisor is designed to run in a CI/CD environment with a native CLI every time new code changes have been integrated into the main branch, so there is continuous checking for available incremental Spring upgrades.

To resolve whether there are incremental upgrades available (for example, from Spring Boot 2.6 to Spring Boot 2.7), Spring Application Advisor checks for the current version of your dependencies and build tools. To retrieve this information with accuracy and to prevent CI failures after an upgrade, Application Advisor must run in a development environment that always has access to your enterprise Maven repositories. This environment is usually the CI/CD environment.

### Architecture

Application Advisor uses its native CLI to calculate the upgrade plans and run the OpenRewrite recipes associated to a plan using the classic Maven and Gradle plug-ins.

The CLI needs connection to the preferred artifact manager tool (Nexus or Artifactory, for example), which resolves the artifacts that contain the recipes.

Application Advisor upgrades the source code running OpenRewrite recipes from the CLI, so no source code is transferred. The CLI resolves which OpenRewrite recipes need to be run, but the artifacts in which those recipes are stored are downloaded from the Maven repositories that you have configured in your environment.

For more information:

- To understand how to integrate the Maven repository into your environment, see Spring Enterprise Repository for Artifact Repository Administrators. Application Advisor runs commercial recipes that are available in the Spring Commercial repository.

---

## Integrating Application Advisor with CI/CD Pipelines

These topics provide the steps for integrating Application Advisor in your CI/CD pipelines:

- Integrating Application Advisor in GitLab Enterprise
- Integrating Application Advisor in GitHub Enterprise
- Integrating Application Advisor in Jenkins
- Integrating with other SaaS CI/CD tools
- Integrating Application Advisor in Bitbucket

### Integrating Application Advisor in GitLab Enterprise

This topic provides the steps for integrating Application Advisor with your CI/CD pipelines in GitLab Enterprise. It explains how to automatically integrate Application Advisor after every build so that manual changes are not required in every pipeline.

**Note:** This topic illustrates the required steps for Google Cloud, but it can be configured in any environment.

#### Step 1: Create a Custom GitLab Runner Using GKE

There are multiple GitLab runners. This section explains the easiest way to integrate the Application Advisor CLI without having to edit the CI/CD pipelines: the Custom GitLab Runner Executor.

1. Create a new Virtual Machine in GKE: Compute Engine - Virtual Machines using an Ubuntu image (available under the Boot disk section).

2. Edit the /etc/hosts to reference the GitLab Instance, if it is not public:
   ```
   <IP VALUE> gitlab.acme.com
   ```

3. Install the gitlab-runner utility for Ubuntu at /home/ folder:
   ```bash
   cd /home/
   curl -L "https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh" | sudo bash
   sudo apt-get install gitlab-runner
   ```

4. Go to your GitLab instance as an administrator, and scroll to the Admin section at the bottom of the screen. Create a new Runner at: CI/CD - Runners - New Instance Runner.

5. Click Linux and Run untagged jobs.

6. Get the token and register the runner with type custom and assign a name at the machine by running:
   ```bash
   sudo gitlab-runner register --url https://gitlab.acme.com --token MY_SECRET_TOKEN --tls-ca-file gitlab.acme.com.crt
   ```

This generates a config file: /etc/gitlab-runner/config.toml.

#### Step 2: Invoke the Advisor CLI from the Custom GitLab Runner

Now that you have created a custom GitLab runner, you need to configure it to run Spring App Advisor.

Follow these steps:

1. Edit the file generated in the previous step: /etc/gitlab-runner/config.toml. Use provided content to configure the custom runner to run a script.

2. Create the folders to be used in the script:
   ```bash
   sudo mkdir /home/gitlab/builds
   sudo chmod -R 777 /home/gitlab/builds
   sudo mkdir /home/gitlab/cache
   sudo chmod -R 777 /home/gitlab/cache
   ```

3. Add a Maven settings file to let Application Advisor connect to the Spring Maven repositories and run the commercial Spring recipes. The Maven settings file should be located in /home/gitlab/.m2/settings.xml.

4. Copy and upload the script (advisor_exec.sh) and the CLI binary (for Linux).

5. Assign permissions:
   ```bash
   sudo chmod +x /home/gitlab/advisor_exec.sh
   sudo chmod +x /home/gitlab/advisor
   ```

6. Make the runner available for the GitLab instance:
   ```bash
   sudo gitlab-runner run NAME-OF-THE-RUNNER
   ```

#### Step 3: Check That Your GitLab Pipelines Run Application Advisor at the End

Go to the GitLab Instance and run a job to check that everything works. This step assumes that the repository containing the job already has a pipeline configured (.gitlab-ci.yml).

### Integrating Application Advisor in GitHub Enterprise

This topic provides the steps for integrating Application Advisor with your CI/CD pipelines in GitHub Enterprise.

You can automatically run scripts on a self-hosted runner, either before a job runs, or after a job finishes running. For instructions for creating a self-hosted runner, see the official GitHub documentation.

1. Modify the ACTIONS_RUNNER_HOOK_JOB_COMPLETED environment variable. There are two ways to set this environment variable:
   - Add it to the operating system:
     ```bash
     ACTIONS_RUNNER_HOOK_JOB_COMPLETED=/opt/runner/advisor_script.sh
     ```
   - Add it to a file named .env in the self-hosted runner application directory.

Create the advisor_script.sh file with the following contents:

```bash
#!/bin/bash
# This script assumes that advisor CLI is in the $PATH
export GIT_TOKEN_FOR_PRS=**WRITE_GIT_ACCESS_TOKEN**
# Check that the $HOME/.m2/settings is using the Spring Commercial repository
advisor build-config get
advisor upgrade-plan apply --push --from-yml
```

2. Ensure that the script has execution permissions:
   ```bash
   chmod u+x /opt/runner/advisor_script.sh
   ```

### Integrating Application Advisor in Jenkins

This topic provides the steps for integrating Application Advisor with your CI/CD pipelines in Jenkins.

Before integrating Application Advisor, check that Jenkins is configured to use the Spring commercial repository in a shared Maven settings.xml file.

See the CloudBees official guide for details about how to provide a shared Maven settings file.

#### Using Pipeline Templates

In Jenkins, Pipeline Templates help ensure that pipeline builds conform to organizational standards. Central or platform teams can create their own standards using a Pipeline Template.

If you are already using CloudBees Pipeline Templates, you can adapt your existing template to include the Application Advisor CLI commands. For example:

```groovy
pipeline {
    agent any
    environment {
        GIT_TOKEN_FOR_PRS = credentials('advisor_git_token_for_prs')
    }
    stages {
        stage(my-stage) {
            steps {
                …
            }
        }
        …
        stage('spring-app-advisor') {
            steps {
                sh 'advisor build-config get'
                sh 'advisor upgrade-plan apply --push --from-yml --token=$GIT_TOKEN_FOR_PRS'
            }
        }
    }
}
```

If you are not using a CloudBees Pipeline Template, create a new Template, and then create a new job for each of the repositories.

### Integrating with Other SaaS CI/CD Tools

For SaaS tools, there is no way to embed a binary in all the builds without altering references in the CI/CD pipeline. Because every CI/CD engine has its own syntax and vocabulary, this topic explains a script-based approach that you must adapt for your solution.

#### Set Up for Script Execution

The goal is to run these CLI commands at the end of the build of the default or main branch:

```bash
...download and extract the advisor CLI…
export GIT_TOKEN_FOR_PRS=**WRITE_GIT_ACCESS_TOKEN**
advisor build-config get
advisor upgrade-plan apply --push --from-yml
```

To set this up, configure the GIT_TOKEN_FOR_PRS environment variable to allow creation of automatic pull requests for upgrading your Spring dependencies, if needed. This must be an access token with write access to the analyzed repository.

Developers decide if they want to receive these pull requests by adding a file named .spring-app-advisor.yml in the root directory.

#### GitHub Actions

This section shows how to apply the script-based approach in the context of GitHub Actions. Note that there is no concrete Java version requirement for running Application Advisor. It must be consistent with the project requirements.

```yaml
name: Spring App Advisor Workflow

on:
  schedule:
    - cron: "0 2 * * 1-5"
  push:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Generates Maven Settings
        uses: 's4u/maven-settings-action@v3.1.0'
        with:
          servers: '[{"id": "tanzu-spring-release", "username": "${{ secrets.BC_USER }}", "password": "${{ secrets.$BC_PWD }}"}]'
          repositories: '[{"id":"tanzu-spring-release", "name":"Spring Enterprise Supported Releases","url":"https://packages.broadcom.com/artifactory/spring-enterprise","snapshots":{"enabled":false}}]'
          pluginRepositories: '[{"id":"tanzu-spring-release", "name":"Spring Enterprise Supported Releases","url":"https://packages.broadcom.com/artifactory/spring-enterprise","snapshots":{"enabled":false}}]'
      - name: Runs Application Advisor
        continue-on-error: true
        env:
          GIT_TOKEN_FOR_PRS: ${{ secrets.advisor_git_token_for_prs }}
          ARTIFACTORY_TOKEN: ${{ secrets.advisor_artifactory_token }}
        run: |
          curl -L -H "Authorization: Bearer $ARTIFACTORY_TOKEN" -o advisor-linux.tar -X GET https://packages.broadcom.com/artifactory/spring-enterprise/com/vmware/tanzu/spring/application-advisor-cli-linux/1.5.4/application-advisor-cli-linux-1.5.4.tar
          tar -xf advisor-linux.tar --strip-components=1 --exclude=./META-INF
          ./advisor build-config get
          ./advisor upgrade-plan apply --push --from-yml --token=$GIT_TOKEN_FOR_PRS
      - name: Get errors if exist
        if: always() && hashFiles('.advisor/errors/') != ''
        run: |
          cat .advisor/errors/*
```

### Integrating Application Advisor in Bitbucket

This topic provides the steps for integrating Application Advisor with your CI/CD pipelines to create pull requests in Bitbucket.

Bitbucket is not a supported Git provider with the --push option. In most cases, you would run these 2 instructions:

```bash
advisor build-config get
advisor upgrade-plan apply --push
```

For Bitbucket, you must write a script that does the following:

1. Creates a branch (e.g., called advisor) if there are changes produced by the tool and the branch does not exist
2. Commits the changes into this new branch
3. Sends pull requests using the Bitbucket REST API

You can copy the full script using the REST API of the last version of the Bitbucket API (2.0). Before running the script, modify the script values for:

- $BB_HOST: Bitbucket host name
- $PROJECT: Bitbucket project
- $SLUG: Bitbucket repository slug
- $ACCESS_TOKEN: Bitbucket access token for writing in the repository

---

## Custom Upgrades Using Application Advisor

Most organizations have shared Java libraries and components across multiple Spring applications. If these shared components use Spring libraries, Application Advisor prevents, by default, upgrading these applications to prevent the introduction of incompatible Spring versions in the classpath.

For example, if you have an application that depends on an internal library called acme-spring-commons allocated in a different Git repository and that library uses spring-boot 2.7.x, this application cannot upgrade to spring-boot 3.0.x until after that library has been upgraded and released with spring-boot 3.0.x.

To allow Spring applications to upgrade the Spring libraries when you upgrade your shared libraries, you must configure the upgrade mappings for those shared libraries.

### Configure the Upgrade Plan for Shared Libraries

To start, create a custom-upgrades-mappings.json file. The configuration contains various properties including:

- **rewriteArtifacts[*].coordinates** - Required. The Maven identifier for the artifact that contains the OpenRewrite recipes.
- **rewriteArtifacts[*].minimalJavaVersion** - Required. The required minimalJavaVersion to run the recipes.
- **initRecipes** - Optional. Array of OpenRewrite recipes that need to be run in the initialization process of any upgrade.
- **projects[*].slug** - Required. The unique project name.
- **projects[*].coordinates** - Required. The list of groupId:artifactId of the coordinates used to reference the Java modules.
- **projects[*].repositoryUrl** - Optional. The URL pointing to the Git repository of the shared libraries.
- **projects[*].rewrite** - Required. Contains the requirements and the OpenRewrite recipes to upgrade from a specific version.
- **projects[*].rewrite.$version.recipes** - Required. Array of OpenRewrite recipes that need to be run simultaneously to upgrade.
- **projects[*].rewrite.$version.requirements.supportedJavaVersions.major** - Required. The major Java version required.
- **projects[*].rewrite.$version.requirements.supportedJavaVersions.minor** - Required. The minor Java version required.
- **projects[*].rewrite.$version.requirements.supportedGenerations** - Required. The list of projects and versions that this project is consuming.
- **projects[*].rewrite.$version.requirements.excludedArtifacts** - Optional. The list of coordinates that are no longer available.
- **projects[*].rewrite.$version.requirements.nextRewrite.version** - Optional. The next version to upgrade to.
- **projects[*].rewrite.$version.requirements.nextRewrite.project** - Optional. The target project.

Alternatively, you can use the experimental command `advisor mapping build`.

One of the main tasks of this command is to discover what versions of the project libraries are available in a Maven repository, and generate the build config metadata for each of the equivalent Git tag versions.

The command to generate the mappings for a repository is run as follows:

```bash
advisor mapping build --repository=${REPO_URL} [--offline] [--maven-server-url=<mavenServerUrl>]
```

### Update the Configuration

There are three options for updating the upgrade mappings:

1. **Provide upgrade mappings stored in the file system**
2. **Provide upgrade mappings located in a Git repository**
3. **Provide upgrade mappings located in JFrog Artifactory**

#### Provide Upgrade Mappings Stored in the File System

This option is only useful if you want to test the upgrade plans and code changes introduced after adding specific upgrade mappings without impacting developer teams.

To configure specific upgrade mappings for your shared libraries/components, create a new environment variable called SPRING_ADVISOR_MAPPING_CUSTOM_0_FILEPATH with the path of your mapping file relative to the location where the commands are run. For example:

```bash
export SPRING_ADVISOR_MAPPING_CUSTOM_0_FILEPATH=relative/path/mapping.json
```

#### Provide Upgrade Mappings Located in a Git Repository

This option is useful for maintaining the upgrade mappings of OSS projects for which your organization does not own the release process, but which your Spring applications are consuming.

To configure the specific upgrade mappings located in a Git repository, create these environment variables:

```bash
export SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_URI=https://github.com/org/repo.git
export SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_TOKEN=${MY_GIT_TOKEN}  # if private
export SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_PATH=mappings/  # optional
export SPRING_ADVISOR_MAPPING_CUSTOM_0_GIT_BRANCH=notmain  # optional
```

#### Provide Upgrade Mappings Located in JFrog Artifactory

App Advisor supports storing and retrieving custom upgrade mappings from JFrog Artifactory generic repositories.

1. Organize your mapping files using the following structure, where each dependency version has its own mapping file.

2. Set the following environment variables to enable custom mapping retrieval:

```bash
export SPRING_ADVISOR_MAPPING_CUSTOM_0_ARTIFACTORY_URI=https://internal.packages.acme.com
export SPRING_ADVISOR_MAPPING_CUSTOM_0_ARTIFACTORY_TOKEN=${ARTIFACTORY_TOKEN}
export SPRING_ADVISOR_MAPPING_CUSTOM_0_ARTIFACTORY_REPOSITORY=acme-mappings-generic-local
export SPRING_ADVISOR_MAPPING_CUSTOM_0_ARTIFACTORY_GAV=com.test.acme:weather-service
```

**Note:** Mapping files must use SemVer versioning.

---

## Recommendations Using Application Advisor

Spring enterprise extensions are not always visible to developers because they are not part of the Spring OSS ecosystem. However, if you already have access to Application Advisor, you also have access to the Spring Enterprise extensions.

Application Advisor includes commands for recommending and applying Spring Enterprise extensions or build tool upgrades (e.g. Java upgrade) that might be useful for helping follow the best practices for running Spring applications.

These commands are:

```bash
advisor advice list
advisor advice apply --name=[adviceId]
```

Connect Application Advisor to your IDE using MCP to make these best practices visible when developers are asking for related topics such as security, compliance, or API management.

---

## Running Commercial Recipes Using OpenRewrite Tools

OpenRewrite is an Open Source Software (OSS) application used for automatically refactoring source code. Spring Application Advisor combines OSS recipes with commercial recipes built by the Spring team. These commercial recipes are available only in the Spring Commercial repository.

This topic provides instructions for running the Spring Commercial OpenRewrite recipes to upgrade Spring applications. The published recipes use org.openrewrite.recipe:rewrite-recipe-bom: 2.23.1. All runtime OSS components use Apache License.

### Upgrade Spring

You can use the following Maven command to run any of the included recipes to upgrade Spring:

```bash
./mvnw -B org.openrewrite.maven:rewrite-maven-plugin:6.22.1:runNoFork -Drewrite.recipeArtifactCoordinates=com.vmware.tanzu.spring.recipes:spring-boot-3-upgrade-recipes:1.5.4 -Drewrite.activeRecipes=<TANZU_SPRING_RECIPE_ID>
```

### Migrate from JAXRS to Spring Boot 3.3

Use the following Maven command:

```bash
./mvnw -B org.openrewrite.maven:rewrite-maven-plugin:6.22.1:runNoFork -Drewrite.recipeArtifactCoordinates=com.vmware.tanzu.spring.recipes:javaee-boot-recipes:1.5.4 -Drewrite.activeRecipes=com.vmware.tanzu.spring.recipes.javaee.jaxrs.MigrateJaxRs
```

**Note:** This recipe is available only for Tanzu Platform users. You must update the repository URL of Spring Enterprise from the Broadcom Support Portal.

### Design Principles

Commercial Spring Recipes follow a couple of design principles that are different from the OSS Spring recipes to avoid duplicated changes and to avoid running unnecessary recipes. These principles are:

- Recipes do not perform steps to upgrade previous steps. For instance, the recipe to upgrade to Spring Boot 3.1.x does not invoke the recipe to upgrade to Spring Boot 3.0.x.
- Recipes do not upgrade downstream projects. The Spring Framework recipes do not upgrade Spring Security. VMware recommends using Application Advisor if you do not want to have to remember what combination of recipes need to be run in your repository.

### Spring Boot Recipes

These are the recipes for upgrading Spring Boot including:

- Spring Boot 3.0.x Recipes
- Spring Boot 3.1.x Recipes
- Spring Boot 3.2.x Recipes
- Spring Boot 3.3.x Recipes
- Spring Boot 3.4.x Recipes
- Spring Boot 3.5.x Recipes
- Spring Boot 4.0.x Recipes

*(Detailed recipe tables are available in the full documentation)*

### Spring Data Recipes

These are the recipes for upgrading Spring Data including:

- Spring Data 3.0.x Recipes
- Spring Data 3.4.x Recipes

### Spring Framework Recipes

These are the recipes for upgrading Spring Framework including:

- Spring Framework 6.0.x Recipes
- Spring Framework 6.1.x Recipes
- Spring Framework 6.2.x Recipes
- Spring Framework 7.0.x Recipes

### Spring Integration Recipes

These are the recipes for upgrading Spring Integration including:

- Spring Integration 6.5.x Recipes

### Spring Security Recipes

These are the recipes for upgrading Spring Security including:

- Spring Security 5.8.x Recipes
- Spring Security 6.0.x Recipes
- Spring Security 6.1.x Recipes
- Spring Security 6.2.x Recipes
- Spring Security 6.3.x Recipes
- Spring Security 6.4.x Recipes
- Spring Security 6.5.x Recipes

---

## Spring Support Insights in Tanzu Hub

Application Advisor can be integrated with Tanzu Hub to help you to view and understand the support status and vulnerabilities of your Spring dependencies across all your Git repositories.

### Connect the Server to Tanzu Hub

This section describes how to connect Application Advisor to Tanzu Hub and how to publish the dependencies of your repositories.

To send data from the CLI to Tanzu Hub:

1. Go to the Tanzu Hub URL of your self-hosted installation.
2. Select the Repositories menu.
3. Click Manage Connections.
4. Click the Attach Spring App Advisor menu.
5. Fill the form with a name and description for your connection. The dialog box prints a set of environment variables. Copy and paste these into the terminal or into the script you use before running Application Advisor.

Your environment variables should follow this format:

```bash
export TANZU_PLATFORM_URL=https://tanzu.acme.com
export TANZU_PLATFORM_OAUTH_APP_ID=<YOUR_APP_KEY>
export TANZU_PLATFORM_OAUTH_APP_SECRET=<YOUR_APP_SECRET>
export TANZU_PLATFORM_API_ID=<YOUR_CONNECTION_ID>
export TANZU_PLATFORM_ORG_ID=<YOUR_ORG_ID>
```

Where:

- TANZU_PLATFORM_URL is the URL location of your Tanzu Platform data instance.
- TANZU_PLATFORM_OAUTH_APP_ID is the OAuth App Id used to send the data.
- TANZU_PLATFORM_OAUTH_APP_SECRET is the OAuth App secret used to send the data.
- TANZU_PLATFORM_API_ID is the App Advisor connection Id associated with the OAuth app.
- TANZU_PLATFORM_ORG_ID is the UUID of the organization registered in Tanzu Hub.

When this is done, you can upload your Git repository build configuration.

Use the following command from the local folder where the Git repository is located:

```bash
advisor build-config publish
```

or alternatively:

```bash
cf repo publish-sbom
```

**Note:** Ensure your current commit exists in the remote Git repository. If you are working on a local branch, push it first using git push.

After the command is run, the support status of your Spring dependencies and the associated vulnerabilities are displayed under Repositories.

### Connecting with Self-Signed Certificates

By default, when using an HTTPS connection, Application Advisor always validates the certificate. If you want to test the tool in an environment with a self-signed certificate, you can add it to the TrustStore:

```bash
openssl s_client -showcerts -connect selfsigned.platform.com:443 </dev/null 2>/dev/null | openssl x509 -outform PEM > server-cert.pem
keytool -import -trustcacerts -keystore myTrustStore.jks -storepass changeit -file server-cert.pem -alias myserver
advisor build-config publish \
  -Djavax.net.ssl.trustStore=myTrustStore.jks \
  -Djavax.net.ssl.trustStorePassword=changeit
```

If you are upgrading your Spring application using the Tanzu cf CLI, you can instead log in using the --skip-ssl-validation option:

```bash
cf login -a $TANZU_PLATFORM_URL --skip-ssl-validation
cf repo publish-sbom
```

### Connecting to Tanzu Hub Through a Proxy

By default, when using an HTTPS connection, the CLI assumes that there is no proxy behind Tanzu Hub. If you want to use the tool in an environment where a proxy is needed, you can configure it as you would for any Java application:

```bash
-Dhttp.proxyHost=proxy.example.com \
-Dhttp.proxyPort=8080 \
-Dhttps.proxyHost=proxy.example.com \
-Dhttps.proxyPort=8080 \
-Dhttp.nonProxyHosts="localhost|127.0.0.1|*.internal.com"
```

---

## IDE Integration Using Model Context Protocol (MCP)

To integrate Application Advisor into your preferred IDE or MCP client, you can use the Application Advisor MCP server.

Application Advisor exposes, through MCP, the required tools to get and apply an upgrade plan or get and apply advice to apply the best practices recommended for Spring applications.

To configure an MCP client, such as Claude Desktop or an IDE (e.g. using MCP plugins) with Application Advisor, copy the following code:

```json
{
  "mcpServers": {
    "advisor-mcp-server": {
      "command": "bash",
      "args": ["-c", "advisor mcp"],
      "transportType": "stdio"
    }
  }
}
```

**Note:** MCP Servers do not inherit the environment variables from its host, so if, for example, the project requires to resolve an environment variable with your private Artifactory, the following has to be included in the MCP Client configuration:

```json
{
  "mcpServers": {
    "advisor-mcp-server": {
      "command": "bash",
      "args": ["-c", "advisor mcp"],
      "transportType": "stdio",
      "env": {
        "ARTIFACTORY_REPOSITORY_URL": "https://my-private.artifactory.com"
      }
    }
  }
}
```

### Connect Application Advisor in Visual Studio

The Cline extension is a Visual Studio Code extension that brings AI-powered coding assistance directly to your editor.

In the MCP Servers tab, add the Application Advisor MCP configuration in a new JSON file called cline_mcp_settings.json.

After that, you should be able to ask for help upgrading the Spring application.

---

## Upgrade Guide for Application Advisor 1.3.x

There have been some important changes in 1.4.x. This topic provides instructions for upgrading from Application Advisor 1.3.x.

### Supporting Migrations Requires Updating the Upgrade Mappings

If you are using custom upgrade mappings for your internal libraries, VMware recommends updating the JSON scheme of those files, but it is not mandatory.

The path you use to define the next version to which to upgrade a project has changed:

- In 1.3.x: `rewrite.$version.requirements.nextRewrite`
- In 1.4.x: `rewrite.$version.requirements.nextRewrite.version`

If you prefer not to do this manually, you can automate this change using a provided script.

---

## Troubleshooting Application Advisor

This topic provides steps for troubleshooting common Application Advisor problems.

### Why Does the Apply Command Report That There Are No Upgrade Plans?

There are two potential causes:

#### There is No Information of the Next Version of Third-Party or Internal Libraries

The most common case is when your application is using components/libraries from other repositories that are using Spring. To find out what third-party components depend on Spring, run:

```bash
advisor upgrade-plan get
```

If the components listed in the output belong to your repositories:

1. Upgrade these components using Application Advisor.
2. Create an OpenRewrite recipe to upgrade the components.
3. Define the mapping between the components and the recipes in Application Advisor.

If the listed components do not belong to your repositories, contact Broadcom Software Support for help.

#### There is No Way to Combine Your Dependencies into Versions That Can Be Aligned

Some upgrades were not included in the upgrade plan. If you prefer to continue with the upgrade and resolve dependency conflicts manually, you can re-run the command with the --accept-no-alignment option.

### Why is My Project Unable to Resolve the New Spring Maven Plugin?

Application Advisor resolves the latest patch version of Spring projects in the configured Maven repositories. If you have configured your Maven repositories to use Application Advisor, it updates the Spring Maven Plugin, and in this case, it might include a commercial version that is available only in the commercial repository.

To solve this problem, VMware recommends ensuring that https://packages.broadcom.com/artifactory/spring-enterprise is accessible as a pluginRepository in your Maven settings file.

### Why is the Application Advisor Mapping Command Unable to Resolve the SBOM?

If the advisor mapping build command fails in specific versions when calculating the SBOM, this is usually because that specific version of the repository:

- Is using artifacts from Maven repositories that are no longer available.
- Is a Maven project that uses the CycloneDX plug-in, and it is producing the SBOM in a different folder than Application Advisor expects.

To distinguish between the two possible causes:

1. Find out the specific Git tag that is failing.
2. Run advisor build-config get -d.

If there is a problem resolving the dependencies, ensure that you have properly configured the repositories that contain the missing dependencies.

### Why is Application Advisor Unable to Resolve the bom.json File?

If the command advisor build-config get is failing with the following message, this is usually because you have a conflicting configuration for the CycloneDX plug-in:

```
java.io.UncheckedIOException: Could not read file: YOUR_REPO_DIR/build/reports/bom.json
```

Application Advisor expects to find the file in:

- For Maven, target/classes/META-INF/sbom
- For Gradle, build/reports

To resolve the problem, replace the output directory with the default value for your system, or move the file.

### Why am I Seeing the "Blocked Mirror for Repositories" Error?

If there are errors in the Maven settings used to download the Spring commercial recipes, the advisor upgrade-plan apply command fails.

If you are using mirror repositories and you see an error about blocked mirrors, this indicates that there might be a rule defined in the global Maven settings file under mirrorOf that is blocking the download.

### Why is My Repository Not Visible in Tanzu Hub?

If you see an error, ensure that you have specified the correct value for the TANZU_PLATFORM_URL environment variable. This needs to be the URL location of your Tanzu Platform data instance (e.g., https://data.platform.tanzu.broadcom.com), not the URL for Tanzu Hub.

### Why Does advisor build-config publish Fail with "Commits That Are Not in the Remote Git Repository"?

The advisor build-config publish command sends your build configuration to Tanzu Hub. Tanzu Hub must be able to verify your current commit in the remote Git repository. If you are on a local-only branch, the command cannot verify the commit and fails.

To resolve the issue, ensure your current commit exists in the remote Git repository before running advisor build-config publish:

1. Push your branch to the remote:
   ```bash
   git push -u origin <branch-name>
   ```

2. Run the publish command again:
   ```bash
   advisor build-config publish
   ```

### Why Does My Build Fail with Checkstyle or NoHttp Errors in the .advisor Directory?

Your build fails with many Checkstyle or NoHttp violations that point at files under .advisor/errors/. This usually happens after an earlier Application Advisor run failed or only partly succeeded.

To fix the issue:

1. Fix the underlying configuration. Examine .advisor/errors/ to find and fix the cause.

2. Remove or clear the .advisor data so the build passes. After you have successfully applied an upgrade plan, remove the .advisor folder:
   ```bash
   rm -rf .advisor
   ```

3. (Optional) If you must keep the .advisor folder, exclude Application Advisor output from Checkstyle in your project's pom.xml.

---

## Application Advisor CLI Reference

This topic provides the list of CLI commands for Application Advisor, along with supported options and examples.

### advisor build-config get

Generates the project build configuration compile-time dependencies and developer tools versions to compile the Java sources of a repository.

**Usage:**
```bash
advisor build-config get [-dh] [-b=<buildTool>] [-p=<path>]
```

**Supported options:**

| Option | Function |
|--------|----------|
| -b, --build-tool=buildTool | Selects the build tool used to resolve the project dependencies (default: mvnw when there are multiple wrappers, and mvn when there are no wrappers) |
| -d, --debug | Prints out debug messages |
| -h, --help | Prints the help for the command options |
| -p, --path=path | Selects the root directory of the source code repository (default: current directory) |

**Examples:**

| Example | Result |
|---------|--------|
| advisor build-config get | Generates the upgrade plan of the repository when the root folder is in the current directory |
| advisor build-config get --path=/home/user/foo | Generates the upgrade plan of the repository when the root folder is in the /home/user/foo directory |

### advisor build-config publish

Publishes an existing build configuration of the source code repository into Tanzu Platform.

**Usage:**
```bash
advisor build-config publish [-h] [-p=<path>]
```

**Note:** The current commit must exist in the remote Git repository. If you are working on a branch that is only available locally, run git push before publishing.

**Supported options:**

| Option | Function |
|--------|----------|
| -h, --help | Prints the help for the command options |
| -p, --path=path | Selects the root directory of the source code repository (default: current directory) |

**Examples:**

| Example | Result |
|---------|--------|
| advisor build-config publish | Publishes the build configuration associated with the repository located in the current directory |

### advisor upgrade-plan get

Prints out the upgrade plan of the source code repository. An upgrade plan is the list of incremental Spring related upgrades that can be performed in isolation.

**Usage:**
```bash
advisor upgrade-plan get [-dfh][--accept-no-alignment][--remove-excluded-artifacts][-p=<path>]
```

**Supported options:**

| Option | Function |
|--------|----------|
| --accept-no-alignment | Not recommended. Creates an upgrade plan, even if there are existing dependency conflicts |
| -d, --debug | Prints out debug messages |
| -h, --help | Prints the help for the command options |
| -f, --force | Forces the resolution of the upgrade plan excluding intermediate dependencies |
| -p, --path=path | Selects the root directory of the source code repository (default: current directory) |
| --remove-excluded-artifacts | Removes excluded artifacts as part of upgrade plan |

**Examples:**

| Example | Result |
|---------|--------|
| advisor upgrade-plan get | Prints the upgrade plan associated to the repository located in the current directory |
| advisor upgrade-plan get --force | Resolves the upgrade plan by ignoring the unrecognized dependencies |
| advisor upgrade-plan get --accept-no-alignment | Applies the first step of an upgrade plan that has conflicting dependencies |

### advisor upgrade-plan apply

Incrementally applies an upgrade plan to the source code repository. This command applies the first step of the upgrade plan to the source code repository.

**Usage:**
```bash
advisor upgrade-plan apply [-dfh] [--from-yml] [--push] [--after-upgrade-cmd=<afterUpgradeRunCommand>] [-b=<buildTool>] [--build-tool-jvm-args=<buildToolJvmArgs>] [--accept-no-alignment][--remove-excluded-artifacts][-p=<path>]
```

**Supported options:**

| Option | Function |
|--------|----------|
| --accept-no-alignment | Applies the first step of an upgrade plan that might have conflicting dependencies |
| --after-upgrade-cmd=afterUpgradeRunCommand | Executes a Maven or Gradle task after applying the upgrade plan |
| -b, --build-tool=buildTool | Selects the build tool used to compile the sources |
| --build-tool-jvm-args=buildToolJvmArgs | Adds JVM arguments to pass into the build tool |
| -d, --debug | Prints out debug messages |
| -f, --force | Forces execution of full upgrade plan, including intermediate dependencies |
| --from-yml | Enables the upgrade plan based on the contents of the .spring-app-advisor.yml file |
| -h, --help | Prints the help for the command options |
| --remove-excluded-artifacts | Removes excluded artifacts as part of upgrade plan |
| -p, --path=path | Selects the root directory of the source code repository (default: current directory) |
| --push | Generates a pull request with the code upgrades |

**Examples:**

| Example | Result |
|---------|--------|
| advisor upgrade-plan apply | Upgrades the repository in the current directory |
| advisor upgrade-plan apply --push | Upgrades the repository and creates a pull request with the changes |
| advisor upgrade-plan apply --push --from-yml | Upgrades the repository and creates a pull request if developers have explicitly enabled automatic updates |
| advisor upgrade-plan apply --force | Upgrades the repository, ignoring version upgrades in intermediate dependencies |
| advisor upgrade-plan apply --accept-no-alignment | Applies the first step of an upgrade plan that has conflicting dependencies |

### advisor mapping build

Generates an upgrade mapping file for a project given its repository.

**Usage:**
```bash
advisor mapping build [-dho] [-b=<buildTool>] [--build-tool-options=<buildToolOptions>] [-c=<coordinate>] -r=<repositoryUrl> [-s=<slug>] [-t=<accessToken>]
```

**Supported options:**

| Option | Function |
|--------|----------|
| -b, --build-tool=buildTool | Selects the build tool used to compile the sources |
| --build-tool-options=buildToolOptions | Build arguments to pass to the build tool |
| -c, --coordinate=coordinate | Main coordinate of the project to check available versions |
| -d, --debug | Prints out debug messages |
| -f, --force | Forces execution of full upgrade plan, including intermediate dependencies |
| -h, --help | Prints the help for the command options |
| --maven-server-url | URL of the internal Maven Server to use |
| -o, --offline | Resolves the versions offline, using the local Maven repository |
| -r, --repository-url=repositoryUrl | Selects the Git repository URL of the project |
| -s, --slug=slug | Name of the project to include into the mapping result |
| -t, --accessToken=accessToken | Personal Access Token for the git repository if needed |

**Examples:**

| Example | Result |
|---------|--------|
| advisor mapping build -r='https://github.com/spring-cloud/spring-cloud-cli' | Generates the upgrade mappings for spring-cloud-cli |

### advisor advice list

Prints out recommendations for following Tanzu Spring best practices.

**Usage:**
```bash
advisor advice list [-h]
```

**Supported options:**

| Option | Function |
|--------|----------|
| -h, --help | Prints the help for the command options |

**Examples:**

| Example | Result |
|---------|--------|
| advisor advice list | List of best practices for deploying your application |

### advisor advice apply

Applies the selected recommendation to follow a Tanzu Spring best practice for a repository.

**Usage:**
```bash
advisor advice apply [-dh] [-b=<buildTool>] [--build-tool-jvm-args=<buildToolJvmArgs>] [--build-tool-options=<buildToolOptions>] -n=<projectName>
```

**Supported options:**

| Option | Function |
|--------|----------|
| -b, --build-tool= | Selects the build tool used to compile the sources |
| --build-tool-jvm-args= | JVM arguments to pass into the build tool |
| --build-tool-options= | Build arguments to pass to the build tool |
| -d, --debug | Prints out debug messages |
| -h, --help | Prints the help to understand the command option |
| -n, --name= | Name of the advice to apply |

**Examples:**

| Example | Result |
|---------|--------|
| advisor advice apply --name="spring-governance-starter" | Adds the Spring Governance Starter dependency and the most convenient setup to start |

### advisor

Base syntax, requires a command.

**Usage:**
```bash
advisor [-v] [?] [COMMAND]
```

**Supported options:**

| COMMAND | Explanation |
|---------|-------------|
| build-config | Generates or publishes build dependencies and tools |
| upgrade-plan | Generates or applies upgrade plan(s) to upgrade the repository code base |
| mapping | Generates the Maven artifacts that belong to a Git repository |
| advice | Generates or applies best practices to deploy Tanzu Spring applications |
| -v, --version | Prints version of Application Advisor CLI |

---

## Documentation Legal Notice

This Documentation, which includes embedded help systems and electronically distributed materials, (hereinafter referred to as the "Documentation") is for your informational purposes only and is subject to change or withdrawal by Broadcom at any time. This Documentation is proprietary information of Broadcom and may not be copied, transferred, reproduced, disclosed, modified or duplicated, in whole or in part, without the prior written consent of Broadcom.

If you are a licensed user of the software product(s) addressed in the Documentation, you may print or otherwise make available a reasonable number of copies of the Documentation for internal use by you and your employees in connection with that software, provided that all Broadcom copyright notices and legends are affixed to each reproduced copy.

The right to print or otherwise make available copies of the Documentation is limited to the period during which the applicable license for such software remains in full force and effect. Should the license terminate for any reason, it is your responsibility to certify in writing to Broadcom that all copies and partial copies of the Documentation have been returned to Broadcom or destroyed.

TO THE EXTENT PERMITTED BY APPLICABLE LAW, BROADCOM PROVIDES THIS DOCUMENTATION "AS IS" WITHOUT WARRANTY OF ANY KIND, INCLUDING WITHOUT LIMITATION, ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR NONINFRINGEMENT. IN NO EVENT WILL BROADCOM BE LIABLE TO YOU OR ANY THIRD PARTY FOR ANY LOSS OR DAMAGE, DIRECT OR INDIRECT, FROM THE USE OF THIS DOCUMENTATION, INCLUDING WITHOUT LIMITATION, LOST PROFITS, LOST INVESTMENT, BUSINESS INTERRUPTION, GOODWILL, OR LOST DATA, EVEN IF BROADCOM IS EXPRESSLY ADVISED IN ADVANCE OF THE POSSIBILITY OF SUCH LOSS OR DAMAGE.

The use of any software product referenced in the Documentation is governed by the applicable license agreement and such license agreement is not modified in any way by the terms of this notice.

The manufacturer of this Documentation is Broadcom Inc.

Provided with "Restricted Rights." Use, duplication or disclosure by the United States Government is subject to the restrictions set forth in FAR Sections 12.212, 52.227-14, and 52.227-19(c)(1) - (2) and DFARS Section 252.227-7014(b)(3), as applicable, or their successors.

Copyright © 2005–2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries. All trademarks, trade names, service marks, and logos referenced herein belong to their respective companies.
