# Spring Boot Enterprise FAQ

## General Questions

### Q: What is the difference between community Spring Boot and Tanzu Spring?

**A:** Community Spring Boot is the open-source version maintained by the Spring team with standard support timelines. Tanzu Spring provides:
- Extended Long-Term Support (LTS) beyond community EOL
- Priority CVE patches and security updates
- 24/7 enterprise support with guaranteed response times
- Curated, tested dependency combinations (Tanzu Spring Runtime)
- Access to Spring experts for technical guidance

### Q: How long is Spring Boot supported?

**A:**
- **Community Support**: Each minor version (e.g., 3.3.x) receives approximately 12 months of support
- **Tanzu Spring**: Extends support to 24-36 months depending on your subscription

### Q: Can I get support for Spring Boot 2.7.x?

**A:** Yes, Tanzu Spring provides extended support for Spring Boot 2.7.x through November 2025, even though community support ended in November 2023.

## Security Questions

### Q: How quickly are CVE patches available?

**A:**
- **Critical CVEs (CVSS 9.0+)**: Patches typically available within 24-48 hours
- **High CVEs (CVSS 7.0-8.9)**: Patches typically available within 1 week
- **Medium/Low CVEs**: Included in regular maintenance releases

### Q: Are CVE patches backported to older versions?

**A:** Yes, Tanzu Spring backports critical and high severity CVE patches to all supported LTS versions.

### Q: How am I notified about security vulnerabilities?

**A:** Enterprise customers receive:
1. Email notifications for critical vulnerabilities
2. Portal alerts in the support dashboard
3. Detailed security advisories with remediation steps
4. For Mission Critical tier: Phone calls for P1 security issues

## Upgrade Questions

### Q: How do I upgrade from Spring Boot 2.x to 3.x?

**A:** The upgrade involves several steps:
1. Upgrade to Java 17 or later (required for Spring Boot 3.x)
2. Update `javax.*` imports to `jakarta.*`
3. Review breaking changes in Spring Boot 3.x release notes
4. Update third-party dependencies for Jakarta EE 9+ compatibility
5. Run comprehensive testing

Tanzu Spring customers can request an upgrade assessment and assistance from support.

### Q: Is there a migration tool for Spring Boot upgrades?

**A:** Yes, several tools can help:
- **OpenRewrite**: Automated code migrations with Spring Boot recipes
- **Spring Boot Migrator**: SBM helps with common migration patterns
- **Tanzu Spring Health Assessment**: Enterprise service that analyzes your application and provides a migration plan

## Performance Questions

### Q: How can I optimize my Spring Boot application?

**A:** Common optimizations include:
1. Enable lazy initialization: `spring.main.lazy-initialization=true`
2. Use virtual threads (Java 21+): `spring.threads.virtual.enabled=true`
3. Optimize JVM settings for your workload
4. Use Spring Boot Actuator metrics to identify bottlenecks
5. Consider GraalVM native compilation for startup time

### Q: Does Tanzu Spring include performance tuning assistance?

**A:** Yes, Premium and Mission Critical support tiers include access to Spring performance experts who can:
- Review your application architecture
- Analyze metrics and identify bottlenecks
- Recommend optimizations
- Assist with load testing and capacity planning

## Integration Questions

### Q: Can I use Tanzu Spring with Kubernetes?

**A:** Absolutely. Spring Boot applications work well with Kubernetes:
- Spring Boot Actuator provides health and readiness probes
- ConfigMaps and Secrets integrate with Spring's externalized configuration
- Spring Cloud Kubernetes provides service discovery and config integration

### Q: Is Spring Cloud included in Tanzu Spring?

**A:** Yes, all Spring Cloud components are covered under Tanzu Spring support, including:
- Spring Cloud Config
- Spring Cloud Gateway
- Spring Cloud Stream
- Spring Cloud Netflix
- Spring Cloud Sleuth/Micrometer

## Billing Questions

### Q: How is Tanzu Spring priced?

**A:** Pricing is based on:
- Number of production application instances
- Support tier (Standard, Premium, Mission Critical)
- Contract length (annual, multi-year)

Contact sales@broadcom.com for a customized quote.

### Q: Can I upgrade my support tier mid-contract?

**A:** Yes, you can upgrade to a higher support tier at any time. Contact your account manager or support to arrange an upgrade.

### Q: Is there a trial available?

**A:** Broadcom offers evaluation periods for qualified organizations. Contact sales to discuss your evaluation needs.

## Support Contact

- Support Portal: https://support.broadcom.com/example
- Sales: sales@broadcom.com
- Documentation: https://docs.vmware.com/en/Tanzu-Spring-Runtime
