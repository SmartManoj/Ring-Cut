# Ring Cut

Android app that declines an incoming call when it is still ringing after N seconds. The network
treats a declined call as busy, so "forward when busy" sends it on, for example to voicemail or an
AI call assistant.

## Why

Some networks ignore the no-reply timer of "forward when unanswered". On Jio (India, tested
2026-11), `**61*<number>**25#` returns "Registration was successful", and `*#61#` shows 25 seconds,
but the network still forwards after about 15 s. With Ring Cut, you choose the ring time.

## Setup

1. Build: `./build.sh`. It needs Android SDK build-tools 35, platform 36 and a JDK, but no Gradle.
   The output is `build/RingCut.apk`.
2. Install: `adb install -r build/RingCut.apk`
3. Allow call access, one time: `adb shell appops set com.smart.ringcut MANAGE_ONGOING_CALLS allow`
4. In the Google Phone app, set "forward when busy" (`**67*<number>#`). Then turn off "forward when
   unanswered" (`#61#`), or the network forwards first. Some dialers, such as Truecaller, do not
   run these codes.
5. Open Ring Cut and pick the ring time (5–60 s).

Keep the ring time under your network's limit for unanswered calls. On Jio, that limit is about
30 s: 30 s failed and 29 s worked.

To set it from adb: `adb shell am start -n com.smart.ringcut/.MainActivity --ei sec 28 --ez on true`

## How it works

Telecom binds a non-UI `InCallService` because the app holds the `MANAGE_ONGOING_CALLS` app-op.
When an incoming call is still ringing after N seconds, the service calls `Call.reject()`, the
same action as a tap on Decline.
