# Changelog
All notable changes to this project will be documented in this file.

2# [v.v.v] - yyyy-mm-dd
3# Added/Removed/Fixed/Changed

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),

## [Unreleased]
### Todo
- About section with settings glossary (bitrate, framerate, etc.)
- Fix frame-jump
- Fix occasional exclusion of first/last frames from batch
- Better 'Time Remaining' estimate

## [0.0.11] - 2019-09-26
### Added
- 'HOW TO USE' button on lytMain :) links to http://workmorer.com/apps/video-time-warp/help
- More status text updates

### Changed
- Rounded GUI corners
- Reordered lytWarp's advanced panel options
- Retitled Warptype to warpmode, so that Invert Warp can become Invert Warp Mode
- Retitled other options to be more explanatory
- Lowered default framerate to 24, faster warping and probably not even noticeable
- Resized some textboxes

### Removed
- Hiding 'scale' option for now as it doesn't actually speed up warp

## [0.0.10] - 2019-09-18
### Added
- Status text that sort of explains progress bar

### Changed
- Limit batchsize to GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS
- Hide non-crucial info from lytWarping, it was distracting. Might make revealable in the future.
- Button labels to more accurately reflect usage

## [0.0.9] - 2019-27-19 what are these dates lol
### Changed
- Enabled SPINTIMER auto shutoff if no warp progress made in 2h, not very well tested but at least safe.

## [0.0.8] - 2019-15-19
### Changed
- Batch size is now calculated by creating frames until a glError is caught

## [0.0.7] - 2019-01-16
### Added
- 'Show/Hide Advanced' in lytWarp

### Fixed
- lytMain should now be hidden via guiByState when lytWarp comes up

## [0.0.6] - 2019-01-15
### Added
- Instrumented Testing with Espresso and GrantPermissionRule

### Changed
- Video now in LinearLayout, stretches to fit (preserves aspect ratio)
- "Invert Warp" changed to "Flip Warp" and moved closer to warpMode spinner
- Permissions requested on btnWarp or btnWatch pressed, toast shown on permission denial

### Fixed
- Foreground Service permission now in manifest

## [0.0.5] - 2018-12-03
### Added
- Warning when overwriting video
- Warning when halting Warper
- SpinTimer logic to prevent infinite spinning of Warper, currently flawed and disabled
- Save all vids in a "Time Warped" folder

### Changed
- Enabled minify for release builds
- seekAmount (seconds) now on exponential scale
- GUI Sliders now round to nearest hundredth
- Log.d calls wrapped in function that does nothing when BuildConfig.DEBUG

### Fixed
- VideoPlayer now checks if file exists before trying to play it
- Helper runOnYes/Cancel methods needed a dialog theme

## [0.0.4] - 2018-11-30
### Added
- "Trim Ends" switch on lytWarp and functionality in Warper
- Toast tip after warping an end-trimmed video by greater than it's duration

### Fixed
- Video is deleted if halted before any frames encoded, Toast notifies user
- Double warning "This video can not be played" is now just toasts, still double I think.
- GUI disappearance when warp halted before any frames encoded

## [0.0.3] - 2018-11-29
### Added
- Two warp modes: Mirror Vertical and Mirror Horizontal

### Fixed
- Video orientation now adjusted for in frag shader
- Aspect ratio scalings on radial warp modes
- Empty portions of lytWarp and lytWarping now toggle gui on touch, same as videoView

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