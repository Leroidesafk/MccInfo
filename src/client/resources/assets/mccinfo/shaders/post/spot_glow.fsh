#version 330

#define Radius 3

#define FinalAlpha 0.1

layout(std140) uniform SamplerInfo {
  vec2 OutSize;
  vec2 InSize;
};

layout(std140) uniform BlurConfig {
  vec2 BlurDir;
};

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

vec2 sampleStep = 1 / InSize * BlurDir;
float gradient = 1.0 / (Radius + 1.0);
bool isFinal = sampleStep.y != 0;
float finalAlpha = isFinal ? FinalAlpha : 1.0;
float additionalAlpha = isFinal ? gradient : 0.0;

void main() {
  vec4 center = texture(InSampler, texCoord);
  if (center.a >= 1.0) {
    fragColor = vec4(center.rgb, finalAlpha);
  } else {
    vec3 blurs;
    float totalColor;
    float alpha = center.a;

    if (alpha > 0.0) {
      totalColor = 1;
      blurs = center.rgb;
    } else {
      totalColor = 0;
      blurs = vec3(0.0);
    }

    for (float r = Radius; r > 0; r--) {
      vec2 stepValue = sampleStep * r;
      vec4 sampleL = texture(InSampler, texCoord - stepValue);
      vec4 sampleR = texture(InSampler, texCoord + stepValue);
      if (sampleL.a > 0.0) {
        totalColor++;
        blurs += sampleL.rgb;
        alpha = max(alpha, sampleL.a - r * gradient);
      }
      if (sampleR.a > 0.0) {
        totalColor++;
        blurs += sampleR.rgb;
        alpha = max(alpha, sampleR.a - r * gradient);
      }
    }

    fragColor = alpha > 0.0 ? vec4(blurs / totalColor, alpha + additionalAlpha) : vec4(0.0);
  }
}
