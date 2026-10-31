#version 330

uniform sampler2D InSampler;

layout(std140) uniform SplitConfig {
  float Spots;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
  vec4 color = texture(InSampler, texCoord);
  ivec3 bits = ivec3(round(color.rgb * 255.0)) & 7;
  bool spot = color.a > 0.0 && bits == ivec3(3, 5, 6);
  fragColor = spot == (Spots > 0.5) ? color : vec4(0.0);
}
