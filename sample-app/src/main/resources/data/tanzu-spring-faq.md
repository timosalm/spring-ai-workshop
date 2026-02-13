# Tanzu Spring FAQ

## Spring General

### Q: What is the version format used for Spring projects?

A: Spring projects do not use semantic versioning but adhere to a version format that follows core principles. The version format is `<major>.<minor>.<patch>(-<qualifier>)`, where:

* `<major>` is incremented for versions that change system requirements and contain large changes. This represents a **major release**, for instance, 5.0.0.
* `<minor>` is incremented for versions that contain new features, but no new system requirements. This represents a **minor release**, also called a **feature release**, for instance, 5.2.0.
* `<patch>` is incremented for versions that provide bug fixes. This represents a **patch release**, also called a **maintenance release**, for instance, 5.2.1.

Tanzu Spring customers also benefit from **hot patch releases**, see below.

### Q: When are Spring projects released?

A: Spring projects follow a strict release cadence. The release train starts **the second Monday of the month** with Micrometer and ends with Spring Boot the Thursday of the week after that. Maintenance releases can happen every month and each project is free to participate in the monthly cadence. You can see the planning of upcoming releases at all times on [calendar.spring.io](https://calendar.spring.io/).

### Q: How frequently are Spring projects released?

A: There are various types of releases and the frequency can be different for each. Here is a summary:

* **Major release**: there is no fixed frequency, however 3+ years is the norm. Spring Framework sets the tone and the rest of the portfolio follows.
* **Feature (minor) release**: happens usually twice a year, once in May and once in November. This is the case for Spring Boot as it manages a large number of dependencies. Spring Framework is more conservative and has one feature release per year, in November.
* **OSS Maintenance (patch) release**: can be as frequent as once per month but each project can determine every month if they are willing to participate in the release train. These are less frequent as they reach the end of the OSS-supported period.
* **Commercial Maintenance (patch) release**: contrary to OSS, a commercially supported version is released at least every quarter (as long as there is one change since the last quarter), which is usually the case for Spring Boot as it handles dependency upgrades.

### Q: What is a "hot patch" release?

A: Hot patch releases are Spring Boot releases that are only available to customers. When a Spring project releases a version that contains a fix for a CVE, it can take a few days before Spring Boot releases a corresponding version. Hot patch releases are released the same day as the corresponding CVE fix and can be used by customers immediately, rather than having to override the version in their project. Hot patch versions have 4 numbers instead of 3 as they are identical to a regular version except for including a CVE fix.

To illustrate this process, let's take an example. Spring Boot **2.7.20** builds against Spring Framework **5.3.32**. On Thursday, the Spring Framework team released Spring Framework **5.3.33** which contains a fix for a CVE. A corresponding Spring Boot **2.7.21** release is scheduled for a week from now. A **Hot Patch 2.7.20.1** Spring Boot release is done that same Thursday. It is the same as Spring Boot **2.7.20**, including Spring Framework **5.3.33**. If another CVE was to be published during that week, another Hot Patch release would be made, **2.7.20.2**.

It is important to note that all of the hot patch versions should be superseded once the main Spring Boot release is done for that release train. In the above example, customers have the choice of upgrading immediately to Spring Boot 2.7.20.1 or waiting until 2.7.21 comes out days later. The goal of these releases is to prevent customers from ever having a CVE fixed in a part of the portfolio that is not included in a Spring Boot version as well.

### Q: Where can I find the changelog of a particular release?

A: Changelogs for open-source versions are usually published on GitHub. Changelogs for commercial releases are published on [enterprise.spring.io](https://enterprise.spring.io). Let's take Spring Boot as an example:

* Open Source: [https://github.com/spring-projects/spring-boot/releases](https://github.com/spring-projects/spring-boot/releases)

### Q: How to determine if a specific version is released in the commercial repository?

A: The answer is different based on the Spring Boot generation targeted by the component. For Spring Boot 2, only Spring Boot is released in the commercial repository. As of Spring Boot 3, all supported projects that have reached their enterprise support phase are released on the commercial repository. The support phases can be determined by looking at their support timeline, `https://spring.io/projects/<project-id>#support`, where `<project-id>` is the identifier of the project. For Spring Data, that would be [https://spring.io/projects/spring-data#support](https://spring.io/projects/spring-data#support).

### Q: Do we have a list of patches/releases for old versions of Spring Boot and Spring Framework that are already EOL?

A: Yes, a list of patches/releases can be found here for [Spring Framework](https://repo1.maven.org/maven2/org/springframework/spring-core/) and here for [Spring Boot](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/). In addition, support timelines and dates can be found at:

* [https://spring.io/projects/spring-boot#support](https://spring.io/projects/spring-boot#support)
* [https://spring.io/projects/spring-framework#support](https://spring.io/projects/spring-framework#support)

## Tanzu Spring

### Q: What is included in Tanzu Spring and how is it priced?

A: Tanzu Spring Support is a commercial support contract for open-source Spring components. This support contract covers the full stack (Java via Liberica, Apache Tomcat, and the Spring libraries) unlike many others in the industry that cannot offer the level of support we can for the application itself. All the information on what is included in Tanzu Spring is available on [enterprise.spring.io](https://enterprise.spring.io), and our documentation.

Here are the highlights:

* **Premium support for the full application stack** - Global, 24x7 support for over 50 Spring projects, plus OpenJDK and Tomcat with fast response times and access to Broadcom docs, resources, and knowledge base.
* **Extended releases for Spring OSS projects** - Regular, scheduled releases give you access to patches, security fixes, and dependency updates for enterprise-supported project versions that are out of open-source support. For Spring Boot 2.7, we provided for example more than 25 patch releases after the last open-source release 2.7.18, and will continue to do so until at least June 2029.
* **Extended releases for Apache Tomcat and OpenJDK (with Bellsoft Liberica OpenJDK)** - Extended support for Apache Tomcat is provided by the top committers on the open-source project for versions which the Apache Software Foundation considers no longer supported, and provides fixes for known CVEs reported for those versions. Tanzu Spring customers are also entitled to use Tanzu tc Server, a fully compatible Apache Tomcat replacement with additional features. Extended support for OpenJDK is provided by Bellsoft's Liberica OpenJDK distribution, a leading OpenJDK distribution.
* **Spring Application Advisor** - A tool designed to perform deterministic upgrades of Spring applications. The tool is proven to cut upgrade efforts by up to 50% through improved UX and our commercial OpenRewrite recipes. Learn more: [https://www.youtube.com/watch?v=RHs2UIAbVnM](https://www.youtube.com/watch?v=RHs2UIAbVnM)
* **Enterprise Spring Cloud components and Spring Extensions** - Our customers can access various commercial solutions developed by the Spring team. The Enterprise Spring Cloud components include a service registry for service discovery, a configuration server for externalized configuration, an API Gateway based on Spring Cloud Gateway, and the Local Authorization Server for testing. Tanzu Spring also provides extensions for your open-source Spring Cloud Gateway-based API Gateway and to validate application dependencies against regulatory standards, ensuring an application meets applicable compliance requirements.

### Q: What does full application stack mean when talking about Tanzu Spring?

A: The full application stack includes: Java, Apache Tomcat, and Spring.

### Q: When is the first commercial release for Spring Boot?

A: The [first commercial release into a private repo](https://tanzu.vmware.com/content/blog/tanzu-spring-runtime-empowering-developers-for-tomorrows-challenges?utm_source=vault&utm_medium=spring&utm_campaign=faq&utm_content=read&utm_term=pmm) is Spring Boot 2.7.20, released March 1, 2024.

### Q: What does Spring Boot 3.x require as a Java dependency?

A: [Java 17+](https://spring.io/blog/2022/05/24/preparing-for-spring-boot-3-0)

### Q: How long do customers have to upgrade their Spring applications?

A: Tanzu Spring customers are guaranteed at least 12 months of overlap in minor versions (3.0.x, 3.1.x, etc.) for them to upgrade.

### Q: Which products are covered under Tanzu Spring support?

A: The official list of all supported projects can be found on the [Spring site](https://spring.vmware.com/projects) and [Spring.io](https://spring.io/support). There is no need to obtain specific binaries from VMware to be supported.

### Q: What does Spring Support include?

* **Binary Downloads** - VMware distribution of OpenJDK or BellSoft Liberica JDK, Apache Tomcat, or Tanzu tc Server, all updates and upgrades.
* **Premium Support** - Global 24x7 support for 50+ Spring projects with fast response times, access to docs, resources, and the knowledge base.

### Q: If you have Tanzu Spring, does it include JDK, or is there an additional charge?

A: Support for BellSoft's OpenJDK distribution, Liberica, is included. Competitors such as Oracle charge for that separately. With Tanzu Spring it is part of your entitlement.

### Q: If you have Tanzu Spring, does it include Apache Tomcat, or is there an additional charge?

A: It is included. Competitors charge for that separately. With Tanzu Spring it is part of your entitlement.

### Q: Which versions are covered under enterprise support?

A: Spring project support is based on time from release and version numbers. Each Spring project provides its support status (along with version numbers and timelines) on the project page. For example, support windows for Spring Framework can be found at [spring.io/projects/spring-framework#support](http://spring.io/projects/spring-framework#support).

### Q: What is the difference between commercial and open-source support for Spring projects?

A: Commercial support runs for a minimum of 12 months more than open-source support dates. Long Term Support (commercial support) also provides quarterly releases through the life of the support timeline.

### Q: What support can customers expect with zero-day vulnerabilities?

A: "Zero-day vulnerabilities" are vulnerabilities that are public but do not have a patch. As it is our policy not to issue a CVE until a patch is made available, zero-day vulnerabilities typically are not a concern for our users.

### Q: Is there any SLA for releasing bugs/security fixes once identified?

A: There is a 24-hour SLA to respond to the initial report. From there, we work through it with the reporter and other stakeholders. Support SLAs can be found on the [Tanzu Support Services Offerings](https://tanzu.vmware.com/support/offerings) page.

### Q: What is the difference between enterprise (commercial) and open-source support for Spring projects?

A: Enterprise support provides at least an additional 12 months of support (including access to bug fixes and security patches) beyond the initial 12 months of support provided in OSS. It also guarantees a minimum of 12 months of support overlap to give you time to upgrade at your pace. Open-source [support for Spring Boot 2.7 ended on November 25, 2023](https://spring.io/blog/2022/05/24/preparing-for-spring-boot-3-0). [OSS support for Spring 3.1.x ended May 18, 2024.](https://spring.io/projects/spring-boot#support)

### Q: How are upgrades and security patches handled for OpenJDK distribution?

A: New binaries will be available from VMware's [Distribution of OpenJDK](https://network.tanzu.vmware.com/products/pivotal-openjdk). Alternatively, Bellsoft's Liberica JDK/JRE binaries can also be downloaded directly from their [website](https://bell-sw.com/). These binaries are also supported by VMware.

### Q: How are upgrades and security patches handled for Spring projects?

A: New binaries will be available from [repo.spring.vmware.com](https://repo.spring.vmware.com/) for versions that are in commercial support and are aligned with Spring Boot 3+. For versions aligned with Spring Boot 2.x, new binaries are released to Maven Central except for Spring Boot itself.
