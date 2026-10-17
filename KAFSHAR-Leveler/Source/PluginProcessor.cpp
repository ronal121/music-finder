#include "PluginProcessor.h"
#include "PluginEditor.h"
#include <cmath>

KAFSHARLevelerAudioProcessor::KAFSHARLevelerAudioProcessor()
    : AudioProcessor(BusesProperties()
        .withInput("Input", juce::AudioChannelSet::stereo(), true)
        .withOutput("Output", juce::AudioChannelSet::stereo(), true)),
      parameters(*this, nullptr, "PARAMETERS", createParameterLayout())
{
}

juce::AudioProcessorValueTreeState::ParameterLayout
KAFSHARLevelerAudioProcessor::createParameterLayout()
{
    juce::AudioProcessorValueTreeState::ParameterLayout layout;
    layout.add(std::make_unique<juce::AudioParameterFloat>(
        "targetDb", "Target Level", juce::NormalisableRange<float>(-36.0f, 0.0f, 0.1f), -12.0f, "dB"));
    layout.add(std::make_unique<juce::AudioParameterFloat>(
        "responseMs", "Response", juce::NormalisableRange<float>(5.0f, 500.0f, 1.0f, 0.5f), 120.0f, "ms"));
    layout.add(std::make_unique<juce::AudioParameterFloat>(
        "maxBoostDb", "Max Boost", juce::NormalisableRange<float>(0.0f, 24.0f, 0.1f), 12.0f, "dB"));
    layout.add(std::make_unique<juce::AudioParameterFloat>(
        "maxCutDb", "Max Cut", juce::NormalisableRange<float>(0.0f, 24.0f, 0.1f), 18.0f, "dB"));
    return layout;
}

void KAFSHARLevelerAudioProcessor::prepareToPlay(double sampleRate, int)
{
    currentSampleRate = sampleRate > 0.0 ? sampleRate : 44100.0;
    envelope = 0.0f;
    smoothedGain = 1.0f;
}

bool KAFSHARLevelerAudioProcessor::isBusesLayoutSupported(const BusesLayout& layouts) const
{
    const auto input = layouts.getMainInputChannelSet();
    const auto output = layouts.getMainOutputChannelSet();
    return input == output && (input == juce::AudioChannelSet::mono()
                            || input == juce::AudioChannelSet::stereo());
}

void KAFSHARLevelerAudioProcessor::processBlock(juce::AudioBuffer<float>& buffer,
                                                 juce::MidiBuffer&)
{
    juce::ScopedNoDenormals noDenormals;
    const int channels = buffer.getNumChannels();
    const int samples = buffer.getNumSamples();
    if (channels == 0 || samples == 0)
        return;

    const float targetDb = parameters.getRawParameterValue("targetDb")->load();
    const float responseMs = parameters.getRawParameterValue("responseMs")->load();
    const float maxBoostDb = parameters.getRawParameterValue("maxBoostDb")->load();
    const float maxCutDb = parameters.getRawParameterValue("maxCutDb")->load();
    const float target = juce::Decibels::decibelsToGain(targetDb);
    const float minGain = juce::Decibels::decibelsToGain(-maxCutDb);
    const float maxGain = juce::Decibels::decibelsToGain(maxBoostDb);

    // RMS-style envelope follower with smoothed gain. A linked detector keeps
    // stereo image stable by applying the same gain to every channel.
    const float timeSeconds = juce::jmax(0.005f, responseMs * 0.001f);
    const float coefficient = std::exp(-1.0f / static_cast<float>(currentSampleRate * timeSeconds));
    const float gainSmoothing = std::exp(-1.0f / static_cast<float>(currentSampleRate * juce::jmax(0.010f, timeSeconds * 0.35f)));

    for (int i = 0; i < samples; ++i)
    {
        float peakSquared = 0.0f;
        for (int ch = 0; ch < channels; ++ch)
        {
            const float x = buffer.getReadPointer(ch)[i];
            peakSquared = juce::jmax(peakSquared, x * x);
        }

        envelope = coefficient * envelope + (1.0f - coefficient) * peakSquared;
        const float measured = std::sqrt(juce::jmax(envelope, 1.0e-12f));
        const float desiredGain = juce::jlimit(minGain, maxGain, target / measured);
        smoothedGain = gainSmoothing * smoothedGain + (1.0f - gainSmoothing) * desiredGain;

        for (int ch = 0; ch < channels; ++ch)
            buffer.getWritePointer(ch)[i] *= smoothedGain;
    }
}

juce::AudioProcessorEditor* KAFSHARLevelerAudioProcessor::createEditor()
{
    return new KAFSHARLevelerAudioProcessorEditor(*this);
}

void KAFSHARLevelerAudioProcessor::getStateInformation(juce::MemoryBlock& destData)
{
    auto state = parameters.copyState();
    std::unique_ptr<juce::XmlElement> xml(state.createXml());
    copyXmlToBinary(*xml, destData);
}

void KAFSHARLevelerAudioProcessor::setStateInformation(const void* data, int sizeInBytes)
{
    std::unique_ptr<juce::XmlElement> xml(getXmlFromBinary(data, sizeInBytes));
    if (xml != nullptr && xml->hasTagName(parameters.state.getType()))
        parameters.replaceState(juce::ValueTree::fromXml(*xml));
}

juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter()
{
    return new KAFSHARLevelerAudioProcessor();
}
