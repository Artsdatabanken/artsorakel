# Contributing to Artsorakel

Thank you for your interest in contributing to Artsorakel! This document provides guidelines for contributing to the project.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Set up the development environment following the [README](README.md)
4. Create a new branch for your changes

## Development Workflow

### Before Making Changes

1. Check existing [issues](../../issues) to see if your idea or bug has been discussed
2. For significant changes, open an issue first to discuss the approach
3. Run the sync script to ensure resources are up to date:
   ```bash
   python3 sync_resources.py
   ```

### Making Changes

1. Keep changes focused and atomic - one feature or fix per pull request
2. Follow the existing code style and conventions
3. Test your changes on both platforms when possible
4. Update documentation if needed

### Shared Resources

Resources in the `shared/` directory are the source of truth and get synced to both platforms:

- **strings.csv** - Add translations here, not in platform-specific files
- **vectors/** - Add SVG icons here (will be converted to Android VectorDrawables)
- **content/** - HTML content for about pages, FAQ, etc.
- **designsystem/** - Design tokens and color definitions

After modifying shared resources, run `python3 sync_resources.py` to update platform files.

### Code Style

**Kotlin (Android)**
- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names

**Swift (iOS)**
- Follow [Swift API Design Guidelines](https://swift.org/documentation/api-design-guidelines/)
- Use SwiftUI best practices

## Submitting Changes

1. Commit your changes with clear, descriptive commit messages
2. Push to your fork
3. Open a pull request against the `main` branch
4. Describe your changes and link any related issues

### Pull Request Guidelines

- Provide a clear description of the changes
- Include screenshots for UI changes
- Ensure the sync script runs without errors
- Test on all affected platforms before submitting

## Reporting Issues

When reporting bugs, please include:

- Device and OS version
- App version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots if applicable

## Questions?

Feel free to open an issue for questions or reach out to [support@artsobservasjoner.no](mailto:support@artsobservasjoner.no).

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
