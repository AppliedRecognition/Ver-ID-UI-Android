# Contributing to Ver-ID-UI-Android

Thank you for your interest in contributing to Ver-ID-UI-Android! This document provides guidelines and information for contributors.

## 🚀 Getting Started

### Prerequisites
- [Android Studio 4](https://developer.android.com/studio) with Gradle plugin version 4.0.0 or newer
- [Git](https://git-scm.com)
- [Python 3](https://www.python.org/) (for translation tools)

### Setting up the development environment
1. Fork the repository
2. Clone your fork locally
3. Open the project in Android Studio
4. Sync Gradle files and resolve dependencies

## 📝 How to Contribute

### 1. Reporting Issues
- Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md)
- Provide detailed steps to reproduce the issue
- Include device information and Android version
- Attach relevant logs or screenshots

### 2. Suggesting Enhancements
- Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.md)
- Describe the enhancement and its benefits
- Provide use cases and examples

### 3. Code Contributions
- Create a feature branch from `master`
- Follow the existing code style and conventions
- Write clear commit messages
- Include tests for new functionality
- Update documentation as needed

### 4. Translation Contributions
We welcome contributions to add new language support!

#### How to Add a New Language
1. Use the provided Python scripts to generate translation templates
2. Translate the strings to your target language
3. Place the translation file in `veridui/src/main/assets/`
4. Test your translation using the test script
5. Submit a pull request

#### Translation Tools
- `translations.py` - Extracts translatable strings from Java source
- `translation_xml.py` - Generates XML template for new languages
- `test_translation.py` - Validates translation completeness

#### Example: Adding Spanish Support
```bash
# Generate translation template
python3 translation_xml.py > es.xml

# Edit es.xml with Spanish translations
# Test the translation
python3 test_translation.py es.xml

# Move to assets folder
mv es.xml veridui/src/main/assets/
```

#### Supported Languages
- French (fr.xml) - ✅ Complete
- Spanish (es.xml) - ✅ Complete (Added by contributors)
- German (de.xml) - ✅ Complete (Added by contributors)
- Hindi (hi.xml) - ✅ Complete (Added by contributors)
- Chinese (zh.xml) - 🚧 In progress
- Japanese (ja.xml) - 🚧 In progress

## 🔧 Development Guidelines

### Code Style
- Follow Android development best practices
- Use meaningful variable and function names
- Add comments for complex logic
- Maintain consistent indentation

### Testing
- Write unit tests for new functionality
- Test on multiple Android versions
- Verify translations work correctly
- Test edge cases and error conditions

### Documentation
- Update README.md for new features
- Document API changes
- Include usage examples
- Update CHANGELOG.md

## 📋 Pull Request Process

1. **Fork and Clone**: Fork the repository and clone your fork
2. **Create Branch**: Create a feature branch from `master`
3. **Make Changes**: Implement your changes following the guidelines
4. **Test**: Ensure all tests pass and functionality works
5. **Commit**: Write clear, descriptive commit messages
6. **Push**: Push your branch to your fork
7. **Submit PR**: Create a pull request with detailed description
8. **Review**: Address any feedback from maintainers

### Commit Message Format
```
type(scope): description

- Use conventional commit types (feat, fix, docs, style, refactor, test, chore)
- Keep description under 72 characters
- Use imperative mood ("add" not "added")
```

### Example Commit Messages
```
feat(translations): add Spanish language support
fix(ui): resolve camera preview crash on Android 12
docs(readme): update installation instructions
style(code): format Java files according to style guide
```

## 🎯 Contribution Areas

### High Priority
- **Bug Fixes**: Critical issues affecting functionality
- **Security**: Vulnerabilities and security improvements
- **Performance**: Optimizations and performance enhancements

### Medium Priority
- **New Languages**: Additional translation support
- **UI Improvements**: Better user experience
- **Documentation**: Improved guides and examples

### Low Priority
- **Code Style**: Formatting and style improvements
- **Minor Features**: Nice-to-have enhancements
- **Refactoring**: Code structure improvements

## 🐛 Known Issues

- Some translation scripts may need Python 3 compatibility updates
- Git LFS issues may affect large asset files
- Build issues on certain Android Studio versions

## 📞 Getting Help

- **Issues**: Use GitHub issues for bugs and feature requests
- **Discussions**: Join community discussions for questions
- **Documentation**: Check the docs folder for detailed guides
- **Wiki**: Visit the project wiki for additional resources

## 🙏 Recognition

Contributors will be recognized in:
- Project README.md
- Release notes
- Contributor statistics
- Special acknowledgments for significant contributions

## 📄 License

By contributing to Ver-ID-UI-Android, you agree that your contributions will be licensed under the same license as the project.

---

Thank you for contributing to Ver-ID-UI-Android! Your contributions help make this project better for everyone.
