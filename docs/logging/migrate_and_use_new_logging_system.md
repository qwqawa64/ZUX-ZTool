# Migrate and Use the New Logging System

## Overview

The project implements `log(String msg)` and `logError(String msg, Throwable th)` in BaseHookModule.java for a long time. 
However, these simple methods are problematic for complex logging situations.

The new logging system is extracted from BaseHookModule and rewritten in Kotlin, which also implements a log4j-style logging system that has 6 log levels: 
trace, debug, info, warning, error and fatal. They have the same meaning of the authentic log4j logging system. The only difference between Android logging
and log4j is verbose V.S. trace and the two words are referring to the same log level.

The hook base has migrated to new logging system and marked the old `log()` and `logError()` as `@Deprecated`. 
So you don't have to bother about introducing the infrastructure. All you have to do is to ensure your modules follows the guideline below:

## Migration Guideline

- Migrate logError(): `logger.error`, any log with stack trace should be replaced with `logger.error`, `logger.fatal` is reserved for further use;
- Migrate log():
    - with "if (DEBUG)" guard:
        - Contains screen position, app package name, touch position, etc. : migrate to `logger.trace`, they are debug information that only required by detailed troubleshooting
        - Does not contain other values, just reveal the internal process of the hook: migrate to `logger.debug`
    - without "if (DEBUG)":
        - Reveals the internal process of the hook: migrate to `logger.debug`
        - Indicates the beginning/end of hook installation, including sub-hook's installation process: migrate to `logger.info`
        - Indicates the beginning/end of the intercept chain: migrate to logger.info
        - Meets an error/unexpected situation such as ClassNotFoundException or NoSuchMethodException, but will not block the intercept chain or hook installation: migrate to `logger.warn`
        - Falls back to another approach, file, location, default value or hook: migrate to `logger.warn`
        - Situation that blocks hook installation or the intercept chain: `logger.error`
  
## For New Modules

- `logger.trace`: **for very specific uses only**, for example, diagnostic hooks and log that contains screen position, touch events, counter and package name.
- `logger.debug`: reveals the internal process of your hook installation and intercept chain. For example, finding an obfuscated method with DexKit and 
invoke a method with `chain.thisObject` pointer.
- `logger.info`: indicated the beginning and end of your hook installation and intercept chain.
- `logger.warn`: log situations unexpected but non-blocking. For example, fallback, downgrade, default value and switch to alternate approach.
- `logger.error`: log situations that blocks the hook installation or interception chain. For example, null objects, unable to find critical methods and illegal values.
- `logger.fatal`: reserved for further use.