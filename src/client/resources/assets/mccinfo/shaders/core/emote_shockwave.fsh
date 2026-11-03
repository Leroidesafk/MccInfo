#version 330

#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float radius = length(texCoord0);
    if (radius > 1.0) {
        discard;
    }
    float width = max(vertexColor.g, 0.02);
    float strength = vertexColor.a;
    float across = (radius - (1.0 - width)) / width;
    float band = 1.0 - clamp(abs(across), 0.0, 1.0);
    band = band * band * (3.0 - 2.0 * band);
    if (band <= 0.002) {
        discard;
    }
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 screen = gl_FragCoord.xy / size;
    vec2 direction = radius > 1.0e-4 ? texCoord0 / radius : vec2(0.0);
    float pixels = 1.0 / max(fwidth(radius), 1.0e-5);
    float ringPixels = width * pixels;
    float push = sin(clamp(across, -1.0, 1.0) * 3.14159265);
    vec2 offset = direction * push * band * strength * min(22.0, ringPixels * 0.45) / size;
    vec2 split = direction * band * strength * min(5.0, ringPixels * 0.08) / size;
    vec3 color;
    color.r = texture(Sampler0, screen + offset + split).r;
    color.g = texture(Sampler0, screen + offset).g;
    color.b = texture(Sampler0, screen + offset - split).b;
    fragColor = vec4(color, band * min(1.0, strength * 3.0));
}
