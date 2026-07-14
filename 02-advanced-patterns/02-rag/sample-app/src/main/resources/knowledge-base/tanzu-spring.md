# VMware Tanzu Spring

Commercial offering for Spring from Broadcom/VMware. Source: https://enterprise.spring.io

Tanzu Spring is the commercial subscription that adds enterprise support, extended releases, security, compliance, and productivity tooling on top of the open-source Spring portfolio, OpenJDK, and Apache Tomcat.

---

## Why Tanzu Spring (Overview)

### The stewards of Spring
All committers for Spring Boot, Spring Framework, and the broader open-source Spring portfolio are part of the Tanzu team. Patches, including day-zero security fixes, come directly from the original authors of the code and are validated for compliance. This is the core security advantage of the subscription.

### Security and compliance built in
Enterprise-grade security and compliance capabilities are delivered through Spring Boot starters. These include FIPS support, PCI-DSS validation, SBOM generation, governance actuators, and TLS cipher validation. These capabilities depend on authentic maintenance by the maintainers rather than automated third-party solutions.

### Reduced upgrade costs with automated patching
Application Advisor automates dependency updates by generating pull requests and integrating with CI pipelines. Upgrades are validated for compliance standards, lowering the cost and risk of keeping applications current.

### Premium support
Global 24/7 support covers more than 50 Spring projects plus OpenJDK and Tomcat. Optional add-ons include Resident Engineers and Extended Expert services.

### Easy setup
Integration with a Maven artifact repository makes commercial artifacts available to standard Maven and Gradle builds.

---

## What's Included (Products)

Tanzu Spring bundles the following offerings:

1. Enterprise Releases and Support (`/lts-releases`)
2. Application Advisor (`/spring-application-advisor`)
3. Enterprise-grade Spring Boot Extensions (`/enterprise-extensions`)
4. Tanzu tc Server (`/tcserver`)
5. Enterprise Spring Cloud Components (`/enterprise-components`)

---

## Enterprise Releases and Support

https://enterprise.spring.io/lts-releases

Extended enterprise support for Spring open-source projects, OpenJDK, and Apache Tomcat. It lets organizations align their upgrade timeline with business and technology goals instead of the standard open-source support windows.

### Support timelines
- **Spring Boot and related projects:** In addition to 13 months of open-source support from release date, VMware Tanzu Spring provides a minimum of 1 additional year of support for minor versions. The final minor version of each major release receives an additional 5 years of enterprise support (7 years).
- **OpenJDK:** support follows the BellSoft Liberica distribution timelines.
- **Tomcat:** extended support for versions no longer maintained by Apache, including CVE fixes.

### Key benefits
- Quarterly releases with security patches and dependency updates.
- Production troubleshooting and bug-fix support.
- Global 24/7 support covering more than 50 Spring projects.
- Access to Broadcom documentation and knowledge resources.
- Maven and Gradle repository integration for easy artifact access.

### Coverage
More than 50 projects are supported, plus the OpenJDK and Apache Tomcat runtimes. The supported projects, grouped by category:

**Runtimes**
- OpenJDK
- Apache Tomcat
- VMware tc Server

**Core framework**
- Spring Framework
- Spring Boot
- Reactor
- AspectJ

**Spring Cloud**
- Spring Cloud Commons
- Spring Cloud Config
- Spring Cloud Netflix
- Spring Cloud Bus
- Spring Cloud CircuitBreaker
- Spring Cloud CloudFoundry
- Spring Cloud Open Service Broker
- Spring Cloud Consul
- Spring Cloud Sleuth
- Spring Cloud Zookeeper
- Spring Cloud Kubernetes
- Spring Cloud OpenFeign
- Spring Cloud Gateway
- Spring Cloud Function
- Spring Cloud Contract
- Spring Cloud Task
- Spring Cloud Vault
- Spring Cloud Data Flow

**Spring Data**
- Spring Data Commons
- Spring Data KeyValue
- Spring Data LDAP
- Spring Data JDBC
- Spring Data JPA
- Spring Data Relational
- Spring Data MongoDB
- Spring Data Redis
- Spring Data REST
- Spring Data for Apache Cassandra

**Messaging and streams**
- Spring AMQP
- Spring for Apache Kafka
- Spring for Apache Pulsar
- Spring Cloud Stream
- Spring Cloud Stream Binder for Apache Kafka
- Spring Cloud Stream Binder for RabbitMQ
- Spring Cloud Stream Binder for Kafka Streams
- Spring Cloud Stream Applications

**Security and authorization**
- Spring Security
- Spring Authorization Server
- Spring Session
- Spring CredHub
- Spring Vault
- Spring Kerberos
- Spring LDAP

**Observability and integration**
- Micrometer
- Micrometer Tracing
- Spring Integration

**Web and API**
- Spring for GraphQL
- Spring Web Flow
- Spring Web Services
- Spring HATEOAS
- Spring REST Docs

**Other projects**
- Spring AI
- Spring Batch
- Spring Retry
- Spring Statemachine
- Spring Shell
- Spring Tools

---

## Application Advisor

https://enterprise.spring.io/spring-application-advisor

A tool that streamlines Spring application upgrades by reducing complexity across the whole upgrade lifecycle, from assessment to completion.

### How it works
Application Advisor automatically generates pull requests when new Spring dependency versions become available. Each pull request includes updates to both build configuration and Java source files, so it integrates into CI/CD pipelines without manual work.

### Key features
- **OpenRewrite integration:** uses OpenRewrite recipes internally but hides the complexity. Developers review pull requests instead of finding and configuring recipes themselves.
- **Synchronized multi-project upgrades:** identifies Spring projects that must upgrade together. For example, an app using Spring Web 5.3.x with Spring Security 5.8 is upgraded so both move to 6.0.x together for a successful build.
- **Incremental updates:** enables continuous, small-scale dependency upgrades and API-breaking changes through regular pull requests, keeping systems current with minimal disruption.
- **CI pipeline compatibility:** generated pull requests fit into existing continuous integration workflows.

### Benefit
Development teams can upgrade Spring applications at scale with reduced effort.

Documentation: https://techdocs.broadcom.com/us/en/vmware-tanzu/spring/application-advisor/1-5/app-advisor/what-is-app-advisor.html

---

## Enterprise-grade Spring Boot Extensions

https://enterprise.spring.io/enterprise-extensions

Extensions that let developers add compliance, security, and governance capabilities to Spring Boot applications without sacrificing developer productivity.

### API Gateway extensions
Enterprise extensions for Spring Cloud Gateway that enhance applications with:
- Single Sign-On (SSO)
- Role-based Access Control (RBAC)
- Advanced traffic controls and transformations
- GraphQL support

These are delivered as JAR files containing Spring Cloud Gateway filters, predicates, and Spring Boot actuator information contributors, together with a Bill of Materials (BOM) for simplified dependency management.

### Governance and compliance starter
The VMware Tanzu Spring Boot Governance Starter addresses regulatory audit requirements by validating application dependencies against standards such as:
- FIPS 140-3 (Federal Information Processing Standards)
- NIST 800-53
- PCI-DSS v4 (Payment Card Industry Data Security Standard)
- Other regulatory standards

### Benefit
Developers unlock enhanced compliance, security, and governance without compromising efficiency. The governance starter reduces the burdens traditionally associated with regulatory audits by ensuring applications meet applicable compliance requirements.

---

## Tanzu tc Server

https://enterprise.spring.io/tcserver

A secure, supported, and extended Java application server built on top of Apache Tomcat. It is VMware's enterprise-focused offering for organizations that need reliability in demanding operational environments.

### Relationship to Apache Tomcat
Tanzu tc Server is a complete drop-in replacement for Apache Tomcat. It keeps full compatibility while adding enterprise-grade enhancements. Spring team members have contributed to Tomcat's development for more than 15 years, providing over 75% of commits, bug fixes, and security patches to the open-source project.

### Key operational capabilities
- **Accelerated security updates:** releases security patches ahead of official Apache Software Foundation vulnerability announcements, and sometimes before the associated Tomcat releases.
- **Extended version support:** maintenance for Apache Tomcat versions no longer supported by the ASF, including CVE fixes for legacy versions.
- **Enterprise support coverage:** direct support for open-source Apache Tomcat alongside the proprietary enhancements.

### Benefit
Organizations get a supported, proven servlet container. It is the default application server for Spring Boot, combined with prompt security fixes and extended maintenance for older Tomcat versions.

---

## Enterprise Spring Cloud Components

https://enterprise.spring.io/enterprise-components

Enterprise-ready standalone JARs based on popular Spring Cloud projects, with enhanced security, monitoring, and local development capabilities.

### Enterprise Service Registry
Based on Spring Cloud Netflix Eureka Server. Adds improved configuration for mTLS and TLS support, providing secure service discovery.

### Enterprise Application Configuration
Built on Spring Cloud Config. An externalized configuration server that runs as an executable JAR with mTLS configuration support, simplifying enterprise deployment.

### Enterprise Spring Cloud Gateway
A standalone tool derived from the open-source Spring Cloud Gateway. Adds dynamic route configuration, streamlined single sign-on setup per instance, encrypted TLS communication, and proprietary API route filters for enhanced security.

### Local Authorization Server
Developed from Spring Authorization Server. Enables local token generation without external dependencies such as Okta or Azure Entra. It ships with sane defaults and just enough features to produce access_tokens and id_tokens that look like production tokens.

### Value proposition
These components combine familiar Spring Cloud foundations with commercial enhancements. Organizations keep the benefits of the open-source projects while gaining enterprise-grade features for security, monitoring, and development workflows.

---

## Additional Resources

- Contact / talk to sales: https://go-vmware.broadcom.com/contact-us
- Spring Enterprise subscription and repository configuration: https://techdocs.broadcom.com/us/en/vmware-tanzu/spring/tanzu-spring/commercial/spring-tanzu/spring-enterprise-subscription.html
- Broadcom investment in Spring and Java ecosystem security: https://news.broadcom.com/releases/broadcom-expands-investment-in-spring-and-java-ecosystem-security