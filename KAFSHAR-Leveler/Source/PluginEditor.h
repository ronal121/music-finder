#pragma once
#include <JuceHeader.h>
#include "PluginProcessor.h"

class KAFSHARLevelerAudioProcessorEditor final : public juce::AudioProcessorEditor
{
public:
    explicit KAFSHARLevelerAudioProcessorEditor(KAFSHARLevelerAudioProcessor&);
    ~KAFSHARLevelerAudioProcessorEditor() override = default;
    void paint(juce::Graphics&) override;
    void resized() override;

private:
    KAFSHARLevelerAudioProcessor& audioProcessor;
    juce::Slider targetSlider, responseSlider, boostSlider, cutSlider;
    juce::Label titleLabel, targetLabel, responseLabel, boostLabel, cutLabel;
    using SliderAttachment = juce::AudioProcessorValueTreeState::SliderAttachment;
    std::unique_ptr<SliderAttachment> targetAttachment, responseAttachment, boostAttachment, cutAttachment;
    void configureSlider(juce::Slider&, juce::Label&, const juce::String&);
    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR(KAFSHARLevelerAudioProcessorEditor)
};
