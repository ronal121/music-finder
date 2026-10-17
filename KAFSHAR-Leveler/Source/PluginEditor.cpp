#include "PluginEditor.h"

KAFSHARLevelerAudioProcessorEditor::KAFSHARLevelerAudioProcessorEditor(KAFSHARLevelerAudioProcessor& p)
    : AudioProcessorEditor(&p), audioProcessor(p)
{
    setSize(440, 330);
    titleLabel.setText("KAFSHAR  /  LEVELER", juce::dontSendNotification);
    titleLabel.setFont(juce::Font(juce::FontOptions(23.0f, juce::Font::bold)));
    titleLabel.setColour(juce::Label::textColourId, juce::Colour(0xff56d8e8));
    titleLabel.setJustificationType(juce::Justification::centredLeft);
    addAndMakeVisible(titleLabel);

    configureSlider(targetSlider, targetLabel, "TARGET LEVEL");
    configureSlider(responseSlider, responseLabel, "RESPONSE");
    configureSlider(boostSlider, boostLabel, "MAX BOOST");
    configureSlider(cutSlider, cutLabel, "MAX CUT");

    targetSlider.setRange(-36.0, 0.0, 0.1);
    targetSlider.setTextValueSuffix(" dB");
    responseSlider.setRange(5.0, 500.0, 1.0);
    responseSlider.setTextValueSuffix(" ms");
    boostSlider.setRange(0.0, 24.0, 0.1);
    boostSlider.setTextValueSuffix(" dB");
    cutSlider.setRange(0.0, 24.0, 0.1);
    cutSlider.setTextValueSuffix(" dB");

    targetAttachment = std::make_unique<SliderAttachment>(audioProcessor.parameters, "targetDb", targetSlider);
    responseAttachment = std::make_unique<SliderAttachment>(audioProcessor.parameters, "responseMs", responseSlider);
    boostAttachment = std::make_unique<SliderAttachment>(audioProcessor.parameters, "maxBoostDb", boostSlider);
    cutAttachment = std::make_unique<SliderAttachment>(audioProcessor.parameters, "maxCutDb", cutSlider);
}

void KAFSHARLevelerAudioProcessorEditor::configureSlider(juce::Slider& slider, juce::Label& label,
                                                          const juce::String& text)
{
    label.setText(text, juce::dontSendNotification);
    label.setFont(juce::Font(juce::FontOptions(11.0f, juce::Font::bold)));
    label.setColour(juce::Label::textColourId, juce::Colour(0xffaab8c5));
    label.setJustificationType(juce::Justification::centredLeft);
    addAndMakeVisible(label);
    slider.setSliderStyle(juce::Slider::RotaryHorizontalVerticalDrag);
    slider.setTextBoxStyle(juce::Slider::TextBoxBelow, false, 90, 22);
    slider.setColour(juce::Slider::rotarySliderFillColourId, juce::Colour(0xff22b8cf));
    slider.setColour(juce::Slider::thumbColourId, juce::Colour(0xffd7fbff));
    slider.setColour(juce::Slider::textBoxTextColourId, juce::Colours::white);
    slider.setColour(juce::Slider::textBoxBackgroundColourId, juce::Colour(0xff202b36));
    addAndMakeVisible(slider);
}

void KAFSHARLevelerAudioProcessorEditor::paint(juce::Graphics& g)
{
    g.fillAll(juce::Colour(0xff10171f));
    g.setColour(juce::Colour(0xff243541));
    g.drawRoundedRectangle(getLocalBounds().toFloat().reduced(8.0f), 12.0f, 1.0f);
    g.setColour(juce::Colour(0xff56d8e8));
    g.fillRoundedRectangle(18.0f, 54.0f, getWidth() - 36.0f, 2.0f, 1.0f);
}

void KAFSHARLevelerAudioProcessorEditor::resized()
{
    titleLabel.setBounds(22, 14, getWidth() - 44, 32);
    const int top = 72, cellW = (getWidth() - 48) / 2, cellH = 118;
    targetLabel.setBounds(24, top, cellW, 18);
    targetSlider.setBounds(20, top + 17, cellW, cellH - 12);
    responseLabel.setBounds(24 + cellW, top, cellW, 18);
    responseSlider.setBounds(20 + cellW, top + 17, cellW, cellH - 12);
    boostLabel.setBounds(24, top + cellH, cellW, 18);
    boostSlider.setBounds(20, top + cellH + 17, cellW, cellH - 12);
    cutLabel.setBounds(24 + cellW, top + cellH, cellW, 18);
    cutSlider.setBounds(20 + cellW, top + cellH + 17, cellW, cellH - 12);
}
