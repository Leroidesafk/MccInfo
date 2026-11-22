#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D VanillaSampler;
uniform sampler2D SpotsSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
  vec4 vanilla = texture(VanillaSampler, texCoord);
  vec4 spots = texture(SpotsSampler, texCoord);
  float alpha = spots.a + vanilla.a * (1.0 - spots.a);
  if (alpha <= 0.0) {
    fragColor = vec4(0.0);
  } else {
    fragColor = vec4((spots.rgb * spots.a + vanilla.rgb * vanilla.a * (1.0 - spots.a)) / alpha, alpha);
  }
}
