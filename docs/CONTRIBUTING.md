# Contributing to Firefly EDA Library

Thank you for your interest in contributing to the Firefly Event-Driven Architecture Library! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Contributing Guidelines](#contributing-guidelines)
- [Testing](#testing)
- [Code Style](#code-style)
- [Pull Request Process](#pull-request-process)
- [Release Process](#release-process)

## Code of Conduct

This project adheres to a code of conduct that promotes a welcoming and inclusive environment. By participating, you are expected to uphold this code.

## Getting Started

### Prerequisites

- **Java 21** or higher
- **Maven 3.8+**
- **Docker** (for integration tests)
- **Git**

### Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/firefly-oss/lib-common-eda.git
   cd lib-common-eda
   ```

2. **Build the project**
   ```bash
   mvn clean compile
   ```

3. **Run tests**
   ```bash
   mvn test
   ```

4. **Run integration tests**
   ```bash
   mvn verify
   ```

## Contributing Guidelines

### Types of Contributions

We welcome the following types of contributions:

- **Bug fixes**: Fix issues in existing functionality
- **Feature enhancements**: Improve existing features
- **New features**: Add new capabilities
- **Documentation**: Improve or add documentation
- **Tests**: Add or improve test coverage
- **Performance improvements**: Optimize existing code

### Before You Start

1. **Check existing issues**: Look for existing issues or discussions
2. **Create an issue**: For new features or significant changes, create an issue first
3. **Discuss the approach**: Get feedback on your proposed solution
4. **Fork the repository**: Create your own fork to work on

### Branch Naming

Use descriptive branch names with prefixes:

- `feature/` - New features
- `bugfix/` - Bug fixes
- `docs/` - Documentation changes
- `test/` - Test improvements
- `refactor/` - Code refactoring

Examples:
- `feature/add-protobuf-serializer`
- `bugfix/fix-circular-dependency`
- `docs/update-configuration-guide`

## Testing

### Test Categories

The project includes several types of tests:

1. **Unit Tests**: Test individual components in isolation
2. **Integration Tests**: Test component interactions
3. **Contract Tests**: Test API contracts
4. **Performance Tests**: Test performance characteristics

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=EventPublisherFactoryTest

# Run tests with specific profile
mvn test -Pintegration-tests

# Run tests with coverage
mvn test jacoco:report
```

### Test Requirements

- **All new code must have tests**: Aim for 80%+ coverage
- **Integration tests for new features**: Test real-world scenarios
- **Test edge cases**: Include error conditions and boundary cases
- **Use TestContainers**: For external dependencies (Kafka, RabbitMQ)

### Test Structure

```java
@DisplayName("Should do something when condition is met")
@Test
void shouldDoSomethingWhenConditionIsMet() {
    // Arrange
    // Set up test data and mocks
    
    // Act
    // Execute the code under test
    
    // Assert
    // Verify the results
}
```

## Code Style

### Java Code Style

- **Follow Google Java Style Guide**: Use consistent formatting
- **Use meaningful names**: Classes, methods, and variables should be descriptive
- **Keep methods small**: Aim for single responsibility
- **Use Lombok**: Reduce boilerplate with `@Data`, `@RequiredArgsConstructor`, etc.
- **Add JavaDoc**: Document public APIs

### Example Code Style

```java
/**
 * Publishes events to the configured destination.
 *
 * @param event the event to publish
 * @param destination the destination to publish to
 * @return a Mono that completes when the event is published
 */
@Override
public Mono<Void> publish(Object event, String destination) {
    return validateEvent(event)
            .then(serializeEvent(event))
            .flatMap(serializedEvent -> doPublish(serializedEvent, destination))
            .doOnSuccess(result -> recordMetrics(event, destination))
            .doOnError(error -> handleError(error, event, destination));
}
```

### Configuration

- **Use validation annotations**: `@NotNull`, `@Valid`, etc.
- **Provide sensible defaults**: Make configuration optional where possible
- **Document properties**: Include descriptions and examples

## Pull Request Process

### Before Submitting

1. **Ensure tests pass**: All tests must pass
2. **Update documentation**: Update relevant documentation
3. **Add changelog entry**: Document your changes
4. **Rebase on main**: Ensure your branch is up to date

### Pull Request Template

```markdown
## Description
Brief description of the changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] Tests pass locally
```

### Review Process

1. **Automated checks**: CI/CD pipeline runs automatically
2. **Code review**: At least one maintainer reviews the code
3. **Testing**: Reviewers may test the changes
4. **Approval**: Changes must be approved before merging

## Architecture Guidelines

### Design Principles

- **Reactive Programming**: Use Project Reactor (Mono/Flux)
- **Hexagonal Architecture**: Separate core logic from infrastructure
- **Dependency Injection**: Use Spring's IoC container
- **Configuration**: Externalize all configuration
- **Observability**: Include metrics, health checks, and tracing

### Adding New Publishers

1. **Implement EventPublisher interface**
2. **Add configuration properties**
3. **Create auto-configuration class**
4. **Add health indicator**
5. **Include comprehensive tests**
6. **Update documentation**

### Adding New Consumers

1. **Implement EventConsumer interface**
2. **Add configuration properties**
3. **Handle lifecycle management**
4. **Include error handling**
5. **Add health monitoring**
6. **Create integration tests**

## Release Process

### Versioning

We follow [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes (backward compatible)

### Release Steps

1. **Update version**: Update `pom.xml` version
2. **Update changelog**: Document all changes
3. **Create release branch**: `release/vX.Y.Z`
4. **Final testing**: Run full test suite
5. **Create tag**: Tag the release commit
6. **Deploy artifacts**: Deploy to Maven Central
7. **Update documentation**: Update version references

## Getting Help

### Communication Channels

- **GitHub Issues**: For bugs and feature requests
- **GitHub Discussions**: For questions and general discussion
- **Documentation**: Check existing documentation first

### Maintainers

Current maintainers:
- **Core Team**: @firefly-maintainers

### Response Times

- **Bug reports**: 1-2 business days
- **Feature requests**: 3-5 business days
- **Pull requests**: 2-3 business days

## License

By contributing to this project, you agree that your contributions will be licensed under the Apache License 2.0.

## Recognition

Contributors are recognized in:
- **CHANGELOG.md**: For each release
- **README.md**: Major contributors
- **GitHub contributors page**: Automatic recognition

Thank you for contributing to the Firefly EDA Library!
