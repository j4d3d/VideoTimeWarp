# Changelog
All notable changes to this project will be documented in this file.

2# [v.v.v] - yyyy-mm-dd
3# Added/Removed/Fixed/Changed

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

## [Unreleased]

## [0.0.2] - 2018-11-28
### Added
- Progress bar style
- btnCancel to lytWarp
- btnWarpingWatch to lytWarping

### Changed
- Progress bar now a part of lytWarping
- Estimated time remaining now based on encoded video length

### Fixed
- ScrollViews scale to fit and anchor to bottom
- GUI-selected WarpType no longer resets between warps

## [0.0.1] - 2018-11-26
### Added
- CHANGELOG.md
- Framerate box/slider to activity_main's lytWarp
- Framerate row to lytWarping

### Changed
- lytWarping now updates timeLeft clock
- Slightly improved scrollable layout for tiny phones

### Fixed
- Crash when overwriting source video
- Crash when selected video doesn't actually exist
- Crash when Halt pressed but warping ends at same time