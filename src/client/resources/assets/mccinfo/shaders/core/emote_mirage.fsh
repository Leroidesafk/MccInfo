#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 viewNormal;
in vec3 viewDirection;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
    if (texel.a < 0.1) {
        discard;
    }
    float seconds = GameTime * 1200.0;
    float pulse = 0.5 + 0.5 * sin(seconds * 6.0);
    float facing = abs(dot(normalize(viewNormal), normalize(viewDirection)));
    float rim = pow(1.0 - facing, 2.0);
    float band = fract((gl_FragCoord.y + seconds * 60.0) / 7.0);
    float stripe = smoothstep(0.0, 0.2, band) * (1.0 - smoothstep(0.3, 0.5, band));
    float streak = fract((gl_FragCoord.x * 0.02 + gl_FragCoord.y * 0.35 - seconds * 9.0));
    streak = smoothstep(0.85, 1.0, streak);
    float luma = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
    vec3 tint = vec3(0.56, 0.65, 0.78);
    vec3 color = tint * (0.6 + 0.4 * luma) + vec3(0.22, 0.27, 0.34) * (rim + 0.5 * streak);
    float alpha = 0.3 + 0.24 * stripe + 0.32 * rim + 0.1 * streak + 0.05 * pulse;
    alpha *= vertexColor.a * ColorModulator.a;
    fragColor = apply_fog(vec4(color, alpha), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
