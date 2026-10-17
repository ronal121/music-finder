# KAFSHAR Leveler

A JUCE/C++ VST3 and standalone audio plug-in prototype for FL Studio.

## What it does
- Tracks a smoothed, linked stereo level envelope.
- Automatically raises quieter material and lowers louder material toward a target level.
- Offers Target Level, Response, Max Boost, and Max Cut controls.
- Uses linked stereo gain to help preserve stereo balance.
- Saves and restores plug-in parameters with the host.

This is a real-time leveler prototype, not a transparent offline normalizer. The target is an envelope level, not a guarantee that every individual sample will sit at exactly the target dBFS. Raising quiet material can raise noise, and fast gain changes can affect transients. Always audition and keep headroom.

## Build on Windows
1. Install Visual Studio 2022 with **Desktop development with C++**, and a recent CMake.
2. Clone this repository and open a Developer PowerShell in the repository root.
3. Configure and build:
   `cmake -S KAFSHAR-Leveler -B build -A x64`
   `cmake --build build --config Release --target KAFSHARLeveler_VST3`
4. Find the VST3 bundle under `build/KAFSHARLeveler_artefacts/Release/VST3/`.
5. In FL Studio, add the VST3 folder to the plugin search paths and rescan.

CMake fetches JUCE 8.0.8 at configure time, so the first build requires internet access.

## Initial defaults
- Target Level: -12 dBFS
- Response: 120 ms
- Max Boost: +12 dB
- Max Cut: -18 dB

## Status
Initial source scaffold. Build and audio behavior must be verified on a Windows runner before treating the binary as release-ready.
